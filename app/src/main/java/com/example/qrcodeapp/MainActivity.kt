package com.example.qrcodeapp

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.qrcodeapp.MainViewModel.Leitura
import com.example.qrcodeapp.MainViewModel.UiState
import com.example.qrcodeapp.api.MealDbClient
import com.example.qrcodeapp.databinding.ActivityMainBinding
import com.example.qrcodeapp.db.ConsultaDao
import com.example.qrcodeapp.util.CardapioLinks
import com.example.qrcodeapp.util.Prato
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Tela única do app. Cuida apenas da interface (validação de campos, câmera,
 * compartilhamento); o estado e as chamadas à API ficam no [MainViewModel].
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Preferências: guarda o link do restaurante para reaproveitar entre mesas
    private lateinit var prefs: SharedPreferences

    private val viewModel: MainViewModel by viewModels()

    // Contrato para tirar foto com a câmera do aparelho
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { sucesso ->
        // Se o usuário cancelou, o ViewModel só descarta o arquivo (sem mensagem de erro)
        viewModel.resultadoCamera(sucesso)
    }

    // Contrato para solicitar a permissão de câmera em tempo de execução
    private val permissaoCameraLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedida ->
        if (concedida) {
            abrirCamera()
        } else {
            mostrarMensagem(R.string.erro_permissao_camera)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NOME, MODE_PRIVATE)
        // Só preenche na primeira abertura; ao girar a tela o próprio campo
        // restaura o que o usuário estava digitando.
        if (savedInstanceState == null) {
            binding.edtLink.setText(prefs.getString(PREF_LINK, ""))
        }

        binding.btnGerar.setOnClickListener { gerarQrCode() }
        binding.btnCompartilharQr.setOnClickListener { compartilharQrGerado() }
        binding.btnLimpar.setOnClickListener { limparTela() }
        binding.btnTirarFoto.setOnClickListener { verificarPermissaoEAbrirCamera() }
        binding.btnAbrirCardapio.setOnClickListener {
            (viewModel.ui.value.leitura as? Leitura.Sucesso)?.link?.let(::abrirNoNavegador)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.ui.collect { renderizar(it) } }
                launch { viewModel.mensagens.collect { mostrarMensagem(it) } }
                launch {
                    viewModel.pratosLidos.collect { (qrCode, idPrato) ->
                        consultarPratoEmThread(qrCode, idPrato)
                    }
                }
            }
        }

        binding.btnHistorico.setOnClickListener {
            startActivity(Intent(this, HistoricoActivity::class.java))
        }
        savedInstanceState?.getString(STATE_RESULTADO_PRATO)?.let(::mostrarResultadoPrato)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (binding.txtResultadoPrato.isVisible) {
            outState.putString(STATE_RESULTADO_PRATO, binding.txtResultadoPrato.text.toString())
        }
    }

    // ==================================================================
    // FUNCIONALIDADE 3: CONSULTA DO PRATO LIDO NO QR (Thread + TheMealDB + SQLite)
    // O QR do prato contém o ID dele no TheMealDB (ex.: "52772").
    // ==================================================================

    /**
     * Consulta o prato numa Thread secundária, para não travar a UI Thread.
     * A Thread não pode mexer na tela; por isso as atualizações vão para a
     * UI Thread com runOnUiThread.
     */
    private fun consultarPratoEmThread(qrCode: String, idPrato: String) {
        mostrarMensagem(getString(R.string.consultando_prato, idPrato))
        val dao = ConsultaDao(applicationContext)

        Thread {
            // ----- Thread secundária: rede + banco -----
            var mensagem: String
            var resultado: String? = null
            try {
                val consulta = MealDbClient.consultar(idPrato, qrCode)
                dao.inserir(consulta)  // só consultas bem-sucedidas vão para o histórico
                resultado = getString(R.string.resultado_prato, consulta.nome, consulta.resumo())
                mensagem = getString(R.string.consulta_salva)
            } catch (e: MealDbClient.PratoNaoEncontradoException) {
                mensagem = getString(R.string.erro_prato_nao_encontrado, idPrato)
            } catch (e: IOException) {
                Log.e(TAG, "Falha de rede ao consultar prato", e)
                mensagem = getString(R.string.erro_sem_conexao)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao consultar prato", e)
                mensagem = getString(R.string.erro_generico)
            }

            // ----- De volta à UI Thread -----
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                resultado?.let(::mostrarResultadoPrato)
                mostrarMensagem(mensagem)
            }
        }.start()
    }

    private fun mostrarResultadoPrato(texto: String) {
        binding.txtResultadoPrato.text = texto
        binding.txtResultadoPrato.isVisible = true
    }

    // ==================================================================
    // Desenha a tela a partir do estado do ViewModel
    // ==================================================================
    private fun renderizar(estado: UiState) = with(binding) {
        // ----- Gerar -----
        progressGerar.isVisible = estado.gerando
        btnGerar.isEnabled = !estado.gerando

        val mesa = estado.qrMesa
        val temQr = estado.qrBitmap != null && mesa != null
        imgQrGerado.setImageBitmap(estado.qrBitmap)
        imgQrGerado.isVisible = temQr
        txtPlaceholderQr.isVisible = !temQr
        txtMesaGerada.isVisible = temQr
        txtStatusGeracao.isVisible = temQr
        btnCompartilharQr.isVisible = temQr
        if (temQr) {
            val mesaFormatada = getString(R.string.mesa_formatada, mesa)
            txtMesaGerada.text = mesaFormatada
            txtStatusGeracao.text = getString(R.string.sucesso_qr_gerado, mesaFormatada)
            txtStatusGeracao.setTextColor(cor(R.color.text_primary))
        }

        // ----- Ler -----
        progressLer.isVisible = estado.lendo
        btnTirarFoto.isEnabled = !estado.lendo

        imgFotoCapturada.setImageBitmap(estado.fotoBitmap)
        imgFotoCapturada.isVisible = estado.fotoBitmap != null
        layoutPlaceholderFoto.isVisible = estado.fotoBitmap == null

        when (val leitura = estado.leitura) {
            Leitura.Vazia -> {
                txtResultadoLeitura.text = getString(R.string.resultado_leitura_padrao)
                txtResultadoLeitura.setTextColor(cor(R.color.text_secondary))
            }
            Leitura.Processando -> {
                txtResultadoLeitura.text = getString(R.string.processando)
                txtResultadoLeitura.setTextColor(cor(R.color.text_secondary))
            }
            is Leitura.Sucesso -> {
                txtResultadoLeitura.text = if (leitura.link != null || Prato.extrairId(leitura.texto) != null) {
                    leitura.texto
                } else {
                    // Lido com sucesso, mas não é um link: mostra o conteúdo e avisa
                    "${leitura.texto}\n\n${getString(R.string.erro_sem_link)}"
                }
                txtResultadoLeitura.setTextColor(cor(R.color.text_primary))
            }
            is Leitura.Falha -> {
                txtResultadoLeitura.text = getString(leitura.mensagem)
                txtResultadoLeitura.setTextColor(cor(R.color.text_error))
            }
        }
        btnAbrirCardapio.isVisible = (estado.leitura as? Leitura.Sucesso)?.link != null
    }

    // ==================================================================
    // FUNCIONALIDADE 1: GERAR O QR CODE DA MESA
    // O QR aponta para o cardápio online do restaurante, já com a mesa:
    //   https://meurestaurante.com/cardapio?mesa=12
    // Endpoint: GET https://api.qrserver.com/v1/create-qr-code/?size=..&data=..
    // ==================================================================
    private fun gerarQrCode() {
        val linkBase = binding.edtLink.text?.toString()?.trim().orEmpty()
        val mesa = binding.edtTexto.text?.toString()?.trim().orEmpty()

        binding.edtLink.error = null
        binding.edtTexto.error = null

        if (linkBase.isEmpty()) {
            binding.edtLink.error = getString(R.string.erro_link_vazio)
            return
        }
        if (!CardapioLinks.linkValido(linkBase)) {
            binding.edtLink.error = getString(R.string.erro_link_invalido)
            return
        }
        if (mesa.isEmpty()) {
            binding.edtTexto.error = getString(R.string.erro_mesa_vazia)
            return
        }

        // Guarda o link do restaurante para as próximas mesas
        prefs.edit().putString(PREF_LINK, linkBase).apply()

        viewModel.gerarQr(CardapioLinks.montarUrlMesa(linkBase, mesa), mesa)
    }

    private fun compartilharQrGerado() {
        val arquivo = viewModel.arquivoQrAtual()
        val mesa = viewModel.ui.value.qrMesa
        if (arquivo == null || mesa == null) {
            mostrarMensagem(R.string.erro_sem_qr_para_compartilhar)
            return
        }

        try {
            val uriImagem = FileProvider.getUriForFile(this, "$packageName.fileprovider", arquivo)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uriImagem)
                putExtra(Intent.EXTRA_TEXT, getString(R.string.mesa_formatada, mesa))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.compartilhar_qr_titulo)))
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao compartilhar QR Code", e)
            mostrarMensagem(R.string.erro_generico)
        }
    }

    private fun limparTela() {
        viewModel.limpar()
        binding.txtResultadoPrato.isVisible = false
        binding.edtTexto.text?.clear()
        binding.edtLink.error = null
        binding.edtTexto.error = null
    }

    /** Abre um link (o cardápio lido de um QR) no navegador do aparelho. */
    private fun abrirNoNavegador(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Nenhum navegador para abrir o cardápio", e)
            mostrarMensagem(R.string.erro_abrir_link)
        }
    }

    // ==================================================================
    // FUNCIONALIDADE 2: LER QR CODE (via foto + API)
    // Endpoint: POST https://api.qrserver.com/v1/read-qr-code/?outputformat=json
    // ==================================================================
    private fun verificarPermissaoEAbrirCamera() {
        val temPermissao = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (temPermissao) {
            abrirCamera()
        } else {
            permissaoCameraLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun abrirCamera() {
        val arquivo = try {
            viewModel.novoArquivoFoto()
        } catch (e: IOException) {
            Log.e(TAG, "Erro ao criar arquivo de imagem", e)
            mostrarMensagem(R.string.erro_generico)
            return
        }

        try {
            val uriFoto = FileProvider.getUriForFile(this, "$packageName.fileprovider", arquivo)
            cameraLauncher.launch(uriFoto)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Nenhum app de câmera disponível", e)
            viewModel.resultadoCamera(false)
            mostrarMensagem(R.string.erro_sem_camera)
        }
    }

    // ==================================================================
    // Utilitários
    // ==================================================================
    private fun mostrarMensagem(@StringRes mensagem: Int) {
        Toast.makeText(this, mensagem, Toast.LENGTH_SHORT).show()
    }

    private fun mostrarMensagem(mensagem: String) {
        Toast.makeText(this, mensagem, Toast.LENGTH_SHORT).show()
    }

    private fun cor(id: Int): Int = ContextCompat.getColor(this, id)

    companion object {
        private const val TAG = "QRCodeApp"
        private const val PREFS_NOME = "cardapio_online"
        private const val PREF_LINK = "link_cardapio"
        private const val STATE_RESULTADO_PRATO = "resultado_prato"
    }
}
