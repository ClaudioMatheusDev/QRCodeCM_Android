package com.example.qrcodeapp

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.qrcodeapp.api.RetrofitClient
import com.example.qrcodeapp.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Preferências: guarda o link do restaurante para reaproveitar entre mesas
    private lateinit var prefs: SharedPreferences

    // Link lido no último QR escaneado (usado pelo botão "Abrir cardápio")
    private var linkLido: String? = null

    // Arquivo temporário onde a câmera vai gravar a foto tirada
    private var fotoTemp: File? = null

    companion object {
        private const val PREFS_NOME = "cardapio_online"
        private const val PREF_LINK = "link_cardapio"
    }

    // Contrato para tirar foto com a câmera do aparelho
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { sucesso ->
        if (sucesso) {
            fotoTemp?.let { arquivo -> processarFotoCapturada(arquivo) }
        } else {
            Toast.makeText(this, R.string.erro_generico, Toast.LENGTH_SHORT).show()
        }
    }

    // Contrato para solicitar a permissão de câmera em tempo de execução
    private val permissaoCameraLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedida ->
        if (concedida) {
            abrirCamera()
        } else {
            Toast.makeText(this, R.string.erro_permissao_camera, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NOME, MODE_PRIVATE)
        binding.edtLink.setText(prefs.getString(PREF_LINK, ""))

        binding.btnGerar.setOnClickListener { gerarQrCode() }
        binding.btnTirarFoto.setOnClickListener { verificarPermissaoEAbrirCamera() }
        binding.btnAbrirCardapio.setOnClickListener { abrirNoNavegador(linkLido) }
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

        if (linkBase.isEmpty()) {
            binding.edtLink.error = getString(R.string.erro_link_vazio)
            return
        }
        if (mesa.isEmpty()) {
            binding.edtTexto.error = getString(R.string.erro_mesa_vazia)
            return
        }

        // Conteúdo final que será codificado no QR Code
        val conteudoQr = montarUrlMesa(linkBase, mesa)

        // Guarda o link do restaurante para as próximas mesas
        prefs.edit().putString(PREF_LINK, linkBase).apply()

        // Prepara a UI (equivalente ao onPreExecute)
        binding.progressGerar.visibility = View.VISIBLE
        binding.imgQrGerado.visibility = View.GONE
        binding.txtMesaGerada.visibility = View.GONE
        binding.btnGerar.isEnabled = false

        lifecycleScope.launch {
            try {
                // Processamento em background (equivalente ao doInBackground)
                val bitmap: Bitmap? = withContext(Dispatchers.IO) {
                    val response = RetrofitClient.api.gerarQrCode(dado = conteudoQr)

                    if (response.isSuccessful) {
                        val bytes = response.body()?.bytes()
                        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    } else {
                        null
                    }
                }

                // De volta à Main Thread automaticamente após o withContext
                if (bitmap != null) {
                    binding.imgQrGerado.setImageBitmap(bitmap)
                    binding.imgQrGerado.visibility = View.VISIBLE
                    binding.txtMesaGerada.text =
                        getString(R.string.prefixo_mesa) + " " + mesa
                    binding.txtMesaGerada.visibility = View.VISIBLE
                } else {
                    Toast.makeText(this@MainActivity, R.string.erro_generico, Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e("QRCodeApp", "Erro ao gerar QR Code", e)
                Toast.makeText(this@MainActivity, R.string.erro_generico, Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressGerar.visibility = View.GONE
                binding.btnGerar.isEnabled = true
            }
        }
    }

    /**
     * Junta o link do restaurante com o número da mesa, normalizando o endereço:
     *   "meurestaurante.com/cardapio" + "12"  ->  "https://meurestaurante.com/cardapio?mesa=12"
     */
    private fun montarUrlMesa(linkBase: String, mesa: String): String {
        var base = linkBase
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "https://$base"
        }
        base = base.trimEnd('/')
        val separador = if (base.contains("?")) "&" else "?"
        return "$base${separador}mesa=$mesa"
    }

    /** Abre um link (o cardápio lido de um QR) no navegador do aparelho. */
    private fun abrirNoNavegador(url: String?) {
        if (url.isNullOrEmpty()) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Log.e("QRCodeApp", "Erro ao abrir o cardápio", e)
            Toast.makeText(this, R.string.erro_abrir_link, Toast.LENGTH_SHORT).show()
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
        try {
            val arquivo = criarArquivoTemporario()
            fotoTemp = arquivo

            val uriFoto: Uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                arquivo
            )

            cameraLauncher.launch(uriFoto)
        } catch (e: IOException) {
            Log.e("QRCodeApp", "Erro ao criar arquivo de imagem", e)
            Toast.makeText(this, R.string.erro_generico, Toast.LENGTH_SHORT).show()
        }
    }

    private fun criarArquivoTemporario(): File {
        val diretorio = cacheDir
        return File.createTempFile("qr_foto_", ".jpg", diretorio)
    }

    private fun processarFotoCapturada(arquivo: File) {
        // Mostra a foto tirada na tela
        val bitmapFoto = BitmapFactory.decodeFile(arquivo.absolutePath)
        binding.imgFotoCapturada.setImageBitmap(bitmapFoto)
        binding.imgFotoCapturada.visibility = View.VISIBLE

        binding.progressLer.visibility = View.VISIBLE
        binding.txtResultadoLeitura.text = getString(R.string.processando)
        binding.btnAbrirCardapio.visibility = View.GONE
        binding.btnTirarFoto.isEnabled = false

        lifecycleScope.launch {
            try {
                val textoDecodificado: String? = withContext(Dispatchers.IO) {
                    // Comprime a imagem antes de enviar, economizando dados
                    val imagemComprimida = comprimirBitmap(bitmapFoto)

                    val requestFile = imagemComprimida.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    val part = MultipartBody.Part.createFormData(
                        "file", "qrcode.jpg", requestFile
                    )

                    val response = RetrofitClient.api.lerQrCode(imagem = part)

                    if (response.isSuccessful) {
                        // A API retorna uma lista; pegamos o primeiro símbolo decodificado
                        response.body()
                            ?.firstOrNull()
                            ?.symbol
                            ?.firstOrNull()
                            ?.let { simbolo ->
                                if (simbolo.error != null) null else simbolo.data
                            }
                    } else {
                        null
                    }
                }

                if (textoDecodificado != null) {
                    binding.txtResultadoLeitura.text = textoDecodificado
                    binding.txtResultadoLeitura.setTextColor(
                        ContextCompat.getColor(this@MainActivity, R.color.text_primary)
                    )

                    // Se o QR contém um link, libera o botão "Abrir cardápio"
                    val ehLink = textoDecodificado.startsWith("http://") ||
                        textoDecodificado.startsWith("https://")
                    linkLido = if (ehLink) textoDecodificado else null
                    binding.btnAbrirCardapio.visibility =
                        if (ehLink) View.VISIBLE else View.GONE
                } else {
                    binding.txtResultadoLeitura.text = getString(R.string.erro_sem_link)
                    binding.txtResultadoLeitura.setTextColor(
                        ContextCompat.getColor(this@MainActivity, R.color.text_secondary)
                    )
                    linkLido = null
                    binding.btnAbrirCardapio.visibility = View.GONE
                }

            } catch (e: Exception) {
                Log.e("QRCodeApp", "Erro ao ler QR Code", e)
                binding.txtResultadoLeitura.text = getString(R.string.erro_generico)
            } finally {
                binding.progressLer.visibility = View.GONE
                binding.btnTirarFoto.isEnabled = true
                arquivo.delete()
            }
        }
    }

    /** Reduz o tamanho da imagem para acelerar o upload para a API. */
    private fun comprimirBitmap(bitmap: Bitmap): File {
        val arquivoComprimido = File.createTempFile("qr_upload_", ".jpg", cacheDir)
        val saida = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, saida)
        arquivoComprimido.writeBytes(saida.toByteArray())
        return arquivoComprimido
    }
}
