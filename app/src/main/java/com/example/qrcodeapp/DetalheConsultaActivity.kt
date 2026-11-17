package com.example.qrcodeapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.qrcodeapp.databinding.ActivityDetalheConsultaBinding

/**
 * Segunda tela: mostra todos os dados de um prato do histórico,
 * recebidos da [HistoricoActivity] dentro do Bundle do Intent.
 */
class DetalheConsultaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetalheConsultaBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetalheConsultaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Recupera o Bundle enviado pela tela de histórico
        val dados: Bundle = intent.extras ?: Bundle()
        val naoInformado = getString(R.string.nao_informado)
        fun campo(chave: String) =
            dados.getString(chave).takeUnless { it.isNullOrBlank() } ?: naoInformado

        // O nome vai em destaque no conteúdo; a barra fica só com o botão de voltar
        supportActionBar?.title = ""

        with(binding) {
            txtNome.text = campo(EXTRA_NOME)

            // Chips de categoria e origem: escondidos quando não informados
            txtCategoria.text = dados.getString(EXTRA_CATEGORIA)
            txtCategoria.isVisible = !dados.getString(EXTRA_CATEGORIA).isNullOrBlank()
            txtOrigem.text = dados.getString(EXTRA_ORIGEM)
            txtOrigem.isVisible = !dados.getString(EXTRA_ORIGEM).isNullOrBlank()

            // Um ingrediente por linha, com marcador
            txtIngredientes.text = dados.getString(EXTRA_INGREDIENTES)
                ?.lines()
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.joinToString("\n") { "•  $it" }
                ?.takeIf { it.isNotEmpty() }
                ?: naoInformado
            txtInstrucoes.text = campo(EXTRA_INSTRUCOES)

            txtRegistro.text = getString(R.string.detalhe_registro_valor, dados.getLong(EXTRA_ID))
            txtCodigo.text = campo(EXTRA_ID_PRATO)
            txtData.text = campo(EXTRA_DATA_HORA)
            txtQr.text = campo(EXTRA_QR_CODE)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_QR_CODE = "qr_code"
        const val EXTRA_ID_PRATO = "id_prato"
        const val EXTRA_NOME = "nome"
        const val EXTRA_CATEGORIA = "categoria"
        const val EXTRA_ORIGEM = "origem"
        const val EXTRA_INGREDIENTES = "ingredientes"
        const val EXTRA_INSTRUCOES = "instrucoes"
        const val EXTRA_DATA_HORA = "data_hora"
    }
}
