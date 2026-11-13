package com.example.qrcodeapp.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.qrcodeapp.db.BancoHelper.Companion.COL_CATEGORIA
import com.example.qrcodeapp.db.BancoHelper.Companion.COL_DATA_HORA
import com.example.qrcodeapp.db.BancoHelper.Companion.COL_ID
import com.example.qrcodeapp.db.BancoHelper.Companion.COL_ID_PRATO
import com.example.qrcodeapp.db.BancoHelper.Companion.COL_INGREDIENTES
import com.example.qrcodeapp.db.BancoHelper.Companion.COL_INSTRUCOES
import com.example.qrcodeapp.db.BancoHelper.Companion.COL_NOME
import com.example.qrcodeapp.db.BancoHelper.Companion.COL_ORIGEM
import com.example.qrcodeapp.db.BancoHelper.Companion.COL_QR_CODE
import com.example.qrcodeapp.db.BancoHelper.Companion.TABELA_HISTORICO
import com.example.qrcodeapp.model.ConsultaPrato

/**
 * DAO (Data Access Object): único ponto do app que lê e grava a tabela
 * "historico". As telas não escrevem SQL; só chamam estes métodos.
 */
class ConsultaDao(context: Context) {

    private val helper = BancoHelper(context.applicationContext)

    /** Grava uma consulta bem-sucedida. Retorna o id gerado (ou -1 se falhar). */
    fun inserir(consulta: ConsultaPrato): Long {
        val valores = ContentValues().apply {
            put(COL_QR_CODE, consulta.qrCode)
            put(COL_ID_PRATO, consulta.idPrato)
            put(COL_NOME, consulta.nome)
            put(COL_CATEGORIA, consulta.categoria)
            put(COL_ORIGEM, consulta.origem)
            put(COL_INGREDIENTES, consulta.ingredientes)
            put(COL_INSTRUCOES, consulta.instrucoes)
            put(COL_DATA_HORA, consulta.dataHora)
        }
        return helper.writableDatabase.insert(TABELA_HISTORICO, null, valores)
    }

    /** Lê todas as consultas (mais recentes primeiro) percorrendo um Cursor. */
    fun listarTodas(): List<ConsultaPrato> {
        val lista = mutableListOf<ConsultaPrato>()
        val cursor: Cursor = helper.readableDatabase.query(
            TABELA_HISTORICO,
            null,                 // todas as colunas
            null, null,           // sem WHERE
            null, null,           // sem GROUP BY / HAVING
            "$COL_ID DESC"        // ORDER BY
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                lista.add(
                    ConsultaPrato(
                        id = c.getLong(c.getColumnIndexOrThrow(COL_ID)),
                        qrCode = c.getString(c.getColumnIndexOrThrow(COL_QR_CODE)),
                        idPrato = c.getString(c.getColumnIndexOrThrow(COL_ID_PRATO)),
                        nome = c.getString(c.getColumnIndexOrThrow(COL_NOME)),
                        categoria = c.getString(c.getColumnIndexOrThrow(COL_CATEGORIA)).orEmpty(),
                        origem = c.getString(c.getColumnIndexOrThrow(COL_ORIGEM)).orEmpty(),
                        ingredientes = c.getString(c.getColumnIndexOrThrow(COL_INGREDIENTES)).orEmpty(),
                        instrucoes = c.getString(c.getColumnIndexOrThrow(COL_INSTRUCOES)).orEmpty(),
                        dataHora = c.getString(c.getColumnIndexOrThrow(COL_DATA_HORA))
                    )
                )
            }
        }
        return lista
    }
}
