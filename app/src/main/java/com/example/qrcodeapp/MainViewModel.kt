package com.example.qrcodeapp

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.qrcodeapp.api.RetrofitClient
import com.example.qrcodeapp.util.CardapioLinks
import com.example.qrcodeapp.util.Prato
import com.example.qrcodeapp.util.Imagens
import com.example.qrcodeapp.util.LeitorQrLocal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

/**
 * Guarda o estado da tela e faz as chamadas à API.
 *
 * - Sobrevive à rotação de tela (o ViewModel não é recriado).
 * - Sobrevive à morte do processo (ex.: Android fecha o app enquanto a câmera
 *   está aberta), pois os caminhos dos arquivos e o texto lido ficam no
 *   [SavedStateHandle] e os bitmaps são recarregados do cache.
 */
class MainViewModel(
    app: Application,
    private val estado: SavedStateHandle
) : AndroidViewModel(app) {

    data class UiState(
        val gerando: Boolean = false,
        val qrBitmap: Bitmap? = null,
        val qrMesa: String? = null,
        val lendo: Boolean = false,
        val fotoBitmap: Bitmap? = null,
        val leitura: Leitura = Leitura.Vazia
    )

    sealed interface Leitura {
        data object Vazia : Leitura
        data object Processando : Leitura
        /** [link] é preenchido apenas se o texto lido for um endereço web. */
        data class Sucesso(val texto: String, val link: String?) : Leitura
        data class Falha(@StringRes val mensagem: Int) : Leitura
    }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    /** Mensagens curtas (Toast) que devem ser exibidas uma única vez. */
    private val _mensagens = Channel<Int>(Channel.BUFFERED)
    val mensagens: Flow<Int> = _mensagens.receiveAsFlow()

    /** Leitura de QR com código de prato: (texto do QR, ID do prato). Entregue uma vez só. */
    private val _pratosLidos = Channel<Pair<String, String>>(Channel.BUFFERED)
    val pratosLidos: Flow<Pair<String, String>> = _pratosLidos.receiveAsFlow()

    private var jobGerar: Job? = null
    private var jobLer: Job? = null

    private val cacheDir: File get() = getApplication<Application>().cacheDir
    private val pastaQr: File get() = File(cacheDir, PASTA_QR)
    private val pastaFotos: File get() = File(cacheDir, PASTA_FOTOS)
    private val arquivoUpload: File get() = File(cacheDir, ARQUIVO_UPLOAD)

    /** Erro de resposta da API (HTTP diferente de 2xx ou corpo inválido). */
    private class RespostaInvalidaException : Exception()

    /** A foto tirada não pôde ser lida como imagem. */
    private class FotoInvalidaException : Exception()

    init {
        limparFotosAntigas()
        restaurarEstado()
    }

    // ==================================================================
    // GERAR QR CODE DA MESA
    // ==================================================================
    fun gerarQr(conteudo: String, mesa: String) {
        jobGerar?.cancel()
        estado.remove<String>(KEY_QR_ARQUIVO)
        estado.remove<String>(KEY_QR_MESA)
        _ui.update { it.copy(gerando = true, qrBitmap = null, qrMesa = null) }

        jobGerar = viewModelScope.launch {
            try {
                val (bitmap, arquivo) = withContext(Dispatchers.IO) {
                    val response = RetrofitClient.api.gerarQrCode(dado = conteudo)
                    if (!response.isSuccessful) {
                        response.errorBody()?.close()
                        throw RespostaInvalidaException()
                    }
                    val bytes = response.body()?.bytes() ?: throw RespostaInvalidaException()
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: throw RespostaInvalidaException()

                    // Guarda o PNG no cache: serve para compartilhar e para restaurar a tela.
                    // Apaga o QR anterior para não acumular arquivos.
                    pastaQr.deleteRecursively()
                    pastaQr.mkdirs()
                    val arquivo = File(pastaQr, "qr_mesa_${nomeDeArquivoSeguro(mesa)}.png")
                    arquivo.writeBytes(bytes)
                    bitmap to arquivo
                }
                estado[KEY_QR_ARQUIVO] = arquivo.absolutePath
                estado[KEY_QR_MESA] = mesa
                _ui.update { it.copy(qrBitmap = bitmap, qrMesa = mesa) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RespostaInvalidaException) {
                Log.e(TAG, "Resposta inválida ao gerar QR Code", e)
                _mensagens.send(R.string.erro_servidor)
            } catch (e: IOException) {
                Log.e(TAG, "Falha de rede ao gerar QR Code", e)
                _mensagens.send(R.string.erro_sem_conexao)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao gerar QR Code", e)
                _mensagens.send(R.string.erro_generico)
            } finally {
                _ui.update { it.copy(gerando = false) }
            }
        }
    }

    /** Arquivo PNG do último QR gerado, ou null se não houver. */
    fun arquivoQrAtual(): File? =
        estado.get<String>(KEY_QR_ARQUIVO)?.let(::File)?.takeIf { it.exists() }

    // ==================================================================
    // LER QR CODE (foto + API)
    // ==================================================================

    /**
     * Cria o arquivo onde a câmera vai gravar a foto. O caminho fica salvo no
     * [SavedStateHandle] para não se perder se a Activity/processo for recriado
     * enquanto a câmera está aberta.
     */
    @Throws(IOException::class)
    fun novoArquivoFoto(): File {
        pastaFotos.mkdirs()
        val arquivo = File.createTempFile("qr_foto_", ".jpg", pastaFotos)
        estado[KEY_FOTO_PENDENTE] = arquivo.absolutePath
        return arquivo
    }

    /** Chamado quando a câmera retorna (com ou sem foto). */
    fun resultadoCamera(sucesso: Boolean) {
        val caminho = estado.remove<String>(KEY_FOTO_PENDENTE) ?: return
        val foto = File(caminho)
        if (!sucesso || foto.length() == 0L) {
            // Usuário cancelou: não é erro, só descarta o arquivo vazio
            foto.delete()
            return
        }
        lerQr(foto)
    }

    private fun lerQr(foto: File) {
        jobLer?.cancel()
        estado.remove<String>(KEY_LEITURA_TEXTO)
        _ui.update { it.copy(lendo = true, leitura = Leitura.Processando) }

        jobLer = viewModelScope.launch {
            try {
                // Decodifica reduzida (evita OutOfMemoryError) e grava o JPEG que vai
                // para a API. O mesmo arquivo serve de prévia ao restaurar a tela.
                val (preview, upload) = withContext(Dispatchers.IO) {
                    val bitmap = Imagens.decodificarReduzido(foto, LADO_MAXIMO_FOTO)
                        ?: throw FotoInvalidaException()
                    val upload = arquivoUpload
                    upload.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                    bitmap to upload
                }
                estado[KEY_FOTO_PREVIEW] = upload.absolutePath
                _ui.update { it.copy(fotoBitmap = preview) }

                // 1º tenta a API; se ela falhar ou não achar o QR, decodifica no aparelho
                val texto = lerPelaApi(upload)
                    ?: withContext(Dispatchers.Default) { LeitorQrLocal.decodificar(preview) }

                if (texto == null) {
                    _ui.update { it.copy(leitura = Leitura.Falha(R.string.erro_qr_nao_encontrado)) }
                } else {
                    estado[KEY_LEITURA_TEXTO] = texto
                    val leitura = leituraDe(texto)
                    _ui.update { it.copy(leitura = leitura) }

                    // QR com código de prato (e não um link): a Activity consulta o TheMealDB
                    if (leitura.link == null) {
                        Prato.extrairId(texto)?.let { id -> _pratosLidos.send(texto to id) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FotoInvalidaException) {
                falhaLeitura(R.string.erro_foto_invalida, e)
            } catch (e: OutOfMemoryError) {
                falhaLeitura(R.string.erro_foto_invalida, e)
            } catch (e: Exception) {
                falhaLeitura(R.string.erro_generico, e)
            } finally {
                _ui.update { it.copy(lendo = false) }
                foto.delete()
            }
        }
    }

    /**
     * Envia a foto para a API de leitura. Retorna o texto do QR, ou null se a API
     * falhar (HTTP de erro, rede) ou não encontrar um QR — nesses casos o chamador
     * usa o [LeitorQrLocal] como reserva.
     */
    private suspend fun lerPelaApi(upload: File): String? = withContext(Dispatchers.IO) {
        try {
            val corpo = upload.asRequestBody("image/jpeg".toMediaType())
            val parte = MultipartBody.Part.createFormData("file", "qrcode.jpg", corpo)
            val response = RetrofitClient.api.lerQrCode(imagem = parte)
            if (!response.isSuccessful) {
                Log.w(TAG, "API de leitura respondeu HTTP ${response.code()}; usando leitor local")
                response.errorBody()?.close()
                return@withContext null
            }
            // A API retorna uma lista; pegamos o primeiro símbolo decodificado
            response.body()
                ?.firstOrNull()
                ?.symbol
                ?.firstOrNull()
                ?.takeIf { it.error == null }
                ?.data
                ?.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // IOException (sem rede/timeout) ou JSON inesperado
            Log.w(TAG, "Falha na API de leitura; usando leitor local", e)
            null
        }
    }

    private fun falhaLeitura(@StringRes mensagem: Int, erro: Throwable) {
        Log.e(TAG, "Erro ao ler QR Code", erro)
        _ui.update { it.copy(leitura = Leitura.Falha(mensagem)) }
    }

    // ==================================================================
    // LIMPAR
    // ==================================================================
    fun limpar() {
        jobGerar?.cancel()
        jobLer?.cancel()
        estado.remove<String>(KEY_QR_ARQUIVO)
        estado.remove<String>(KEY_QR_MESA)
        estado.remove<String>(KEY_FOTO_PREVIEW)
        estado.remove<String>(KEY_LEITURA_TEXTO)
        _ui.value = UiState()

        // Arquivos pequenos: pode apagar direto, evitando corrida com uma nova geração
        pastaQr.deleteRecursively()
        arquivoUpload.delete()
    }

    // ==================================================================
    // Restauração / manutenção do cache
    // ==================================================================
    private fun restaurarEstado() {
        estado.get<String>(KEY_LEITURA_TEXTO)?.let { texto ->
            _ui.update { it.copy(leitura = leituraDe(texto)) }
        }

        val caminhoQr = estado.get<String>(KEY_QR_ARQUIVO)
        val mesa = estado.get<String>(KEY_QR_MESA)
        val caminhoPreview = estado.get<String>(KEY_FOTO_PREVIEW)
        if (caminhoQr == null && caminhoPreview == null) return

        viewModelScope.launch {
            val (qr, foto) = withContext(Dispatchers.IO) {
                caminhoQr?.let { BitmapFactory.decodeFile(it) } to
                    caminhoPreview?.let { BitmapFactory.decodeFile(it) }
            }
            if (qr == null) {
                estado.remove<String>(KEY_QR_ARQUIVO)
                estado.remove<String>(KEY_QR_MESA)
            }
            if (foto == null) estado.remove<String>(KEY_FOTO_PREVIEW)
            _ui.update {
                it.copy(
                    qrBitmap = qr,
                    qrMesa = if (qr != null) mesa else null,
                    fotoBitmap = foto
                )
            }
        }
    }

    /** Apaga fotos que ficaram para trás (ex.: app fechado no meio da leitura). */
    private fun limparFotosAntigas() {
        val pendente = estado.get<String>(KEY_FOTO_PENDENTE)
        pastaFotos.listFiles()
            ?.filter { it.absolutePath != pendente }
            ?.forEach { it.delete() }
    }

    private fun leituraDe(texto: String): Leitura.Sucesso {
        val link = texto.trim().takeIf { CardapioLinks.ehLinkWeb(it) }
        return Leitura.Sucesso(texto, link)
    }

    private fun nomeDeArquivoSeguro(texto: String): String =
        texto.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)

    companion object {
        private const val TAG = "QRCodeApp"

        private const val PASTA_QR = "qr_gerado"
        private const val PASTA_FOTOS = "fotos"
        private const val ARQUIVO_UPLOAD = "qr_upload.jpg"
        private const val LADO_MAXIMO_FOTO = 1280

        private const val KEY_QR_ARQUIVO = "qr_arquivo"
        private const val KEY_QR_MESA = "qr_mesa"
        private const val KEY_FOTO_PENDENTE = "foto_pendente"
        private const val KEY_FOTO_PREVIEW = "foto_preview"
        private const val KEY_LEITURA_TEXTO = "leitura_texto"
    }
}
