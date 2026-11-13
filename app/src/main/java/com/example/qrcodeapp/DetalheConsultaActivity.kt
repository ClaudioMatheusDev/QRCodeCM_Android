package com.example.qrcodeapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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

        supportActionBar?.title = campo(EXTRA_NOME)

        binding.txtDetalhes.text = getString(
            R.string.detalhe_formatado,
            dados.getLong(EXTRA_ID),
            campo(EXTRA_NOME),
            campo(EXTRA_ID_PRATO),
            campo(EXTRA_CATEGORIA),
            campo(EXTRA_ORIGEM),
            campo(EXTRA_INGREDIENTES),
            campo(EXTRA_INSTRUCOES),
            campo(EXTRA_DATA_HORA),
            campo(EXTRA_QR_CODE)
        )
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
