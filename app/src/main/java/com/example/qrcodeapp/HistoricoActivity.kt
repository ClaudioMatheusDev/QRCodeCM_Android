package com.example.qrcodeapp

import android.content.Intent
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.qrcodeapp.databinding.ActivityHistoricoBinding
import com.example.qrcodeapp.db.ConsultaDao
import com.example.qrcodeapp.model.ConsultaPrato

/**
 * Lista o histórico de pratos consultados (gravado no SQLite) em uma ListView.
 * Ao tocar em um item, abre a [DetalheConsultaActivity] com os dados num Bundle.
 */
class HistoricoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoricoBinding
    private lateinit var dao: ConsultaDao

    private val consultas = mutableListOf<ConsultaPrato>()
    private lateinit var adapter: ArrayAdapter<ConsultaPrato>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoricoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.titulo_historico)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        dao = ConsultaDao(applicationContext)

        // ArrayAdapter usa o toString() de ConsultaPrato para o texto de cada linha
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, consultas)
        binding.listaHistorico.adapter = adapter

        // Evento de clique: abre a tela de detalhes passando o registro num Bundle
        binding.listaHistorico.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, posicao, _ ->
                abrirDetalhes(consultas[posicao])
            }
    }

    override fun onResume() {
        super.onResume()
        carregarHistorico()
    }

    /** Lê o banco numa Thread secundária e atualiza a ListView na UI Thread. */
    private fun carregarHistorico() {
        Thread {
            val registros = dao.listarTodas()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                consultas.clear()
                consultas.addAll(registros)
                adapter.notifyDataSetChanged()
                binding.listaHistorico.isVisible = registros.isNotEmpty()
                binding.txtVazio.isVisible = registros.isEmpty()
            }
        }.start()
    }

    private fun abrirDetalhes(consulta: ConsultaPrato) {
        val dados = Bundle().apply {
            putLong(DetalheConsultaActivity.EXTRA_ID, consulta.id)
            putString(DetalheConsultaActivity.EXTRA_QR_CODE, consulta.qrCode)
            putString(DetalheConsultaActivity.EXTRA_ID_PRATO, consulta.idPrato)
            putString(DetalheConsultaActivity.EXTRA_NOME, consulta.nome)
            putString(DetalheConsultaActivity.EXTRA_CATEGORIA, consulta.categoria)
            putString(DetalheConsultaActivity.EXTRA_ORIGEM, consulta.origem)
            putString(DetalheConsultaActivity.EXTRA_INGREDIENTES, consulta.ingredientes)
            putString(DetalheConsultaActivity.EXTRA_INSTRUCOES, consulta.instrucoes)
            putString(DetalheConsultaActivity.EXTRA_DATA_HORA, consulta.dataHora)
        }
        val intent = Intent(this, DetalheConsultaActivity::class.java)
        intent.putExtras(dados)
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
