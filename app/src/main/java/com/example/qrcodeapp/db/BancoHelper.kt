package com.example.qrcodeapp.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Cria e atualiza o banco SQLite local do app.
 */
class BancoHelper(context: Context) :
    SQLiteOpenHelper(context, NOME_BANCO, null, VERSAO_BANCO) {

    /** Chamado só na primeira vez em que o banco é aberto: cria as tabelas. */
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABELA_HISTORICO (
                $COL_ID           INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_QR_CODE      TEXT NOT NULL,
                $COL_ID_PRATO     TEXT NOT NULL,
                $COL_NOME         TEXT NOT NULL,
                $COL_CATEGORIA    TEXT,
                $COL_ORIGEM       TEXT,
                $COL_INGREDIENTES TEXT,
                $COL_INSTRUCOES   TEXT,
                $COL_DATA_HORA    TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    /**
     * Chamado quando VERSAO_BANCO aumenta. Como é só um histórico, a estratégia
     * mais simples é apagar e recriar a tabela.
     * (Versão 1 guardava consultas de CEP; versão 2 guarda pratos.)
     */
    override fun onUpgrade(db: SQLiteDatabase, versaoAntiga: Int, versaoNova: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABELA_HISTORICO")
        onCreate(db)
    }

    companion object {
        private const val NOME_BANCO = "consultas_qr.db"
        private const val VERSAO_BANCO = 2

        const val TABELA_HISTORICO = "historico"
        const val COL_ID = "_id"
        const val COL_QR_CODE = "qr_code"
        const val COL_ID_PRATO = "id_prato"
        const val COL_NOME = "nome"
        const val COL_CATEGORIA = "categoria"
        const val COL_ORIGEM = "origem"
        const val COL_INGREDIENTES = "ingredientes"
        const val COL_INSTRUCOES = "instrucoes"
        const val COL_DATA_HORA = "data_hora"
    }
}
