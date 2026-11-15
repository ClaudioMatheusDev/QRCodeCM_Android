package com.example.qrcodeapp.api

import com.example.qrcodeapp.model.ConsultaPrato
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Consulta um prato na API TheMealDB usando HttpURLConnection + BufferedReader + JSONObject.
 *
 * ATENÇÃO: faz acesso à rede de forma BLOQUEANTE. Deve ser chamada apenas de
 * uma Thread secundária (nunca da UI Thread, senão o Android lança
 * NetworkOnMainThreadException).
 *
 * Exemplo de resposta de https://www.themealdb.com/api/json/v1/1/lookup.php?i=52772 :
 * {
 *   "meals": [{
 *     "idMeal": "52772",
 *     "strMeal": "Teriyaki Chicken Casserole",
 *     "strCategory": "Chicken",
 *     "strArea": "Japanese",
 *     "strInstructions": "Preheat oven to 350° F. ...",
 *     "strIngredient1": "soy sauce",  "strMeasure1": "3/4 cup",
 *     ... até strIngredient20 / strMeasure20
 *   }]
 * }
 * Prato inexistente: { "meals": null }
 */
object MealDbClient {

    // "1" é a chave pública de testes/estudo fornecida pelo próprio TheMealDB
    private const val URL_BASE = "https://www.themealdb.com/api/json/v1/1/lookup.php?i="

    /** O código é válido, mas não existe prato com esse ID no TheMealDB. */
    class PratoNaoEncontradoException : Exception()

    @Throws(IOException::class, PratoNaoEncontradoException::class)
    fun consultar(idPrato: String, qrCode: String): ConsultaPrato {
        val url = URL(URL_BASE + URLEncoder.encode(idPrato, "UTF-8"))
        val conexao = url.openConnection() as HttpURLConnection

        try {
            conexao.requestMethod = "GET"
            conexao.connectTimeout = 15_000
            conexao.readTimeout = 15_000
            conexao.setRequestProperty("Accept", "application/json")

            val codigo = conexao.responseCode
            if (codigo != HttpURLConnection.HTTP_OK) {
                throw IOException("TheMealDB respondeu HTTP $codigo")
            }

            // Lê a resposta linha a linha com BufferedReader
            val corpo = StringBuilder()
            BufferedReader(InputStreamReader(conexao.inputStream, Charsets.UTF_8)).use { leitor ->
                var linha = leitor.readLine()
                while (linha != null) {
                    corpo.append(linha).append('\n')
                    linha = leitor.readLine()
                }
            }

            // Converte em JSONObject; "meals" é null quando o prato não existe
            val json = JSONObject(corpo.toString())
            val pratos = json.optJSONArray("meals")
            if (pratos == null || pratos.length() == 0) throw PratoNaoEncontradoException()
            val prato = pratos.getJSONObject(0)

            return ConsultaPrato(
                qrCode = qrCode,
                idPrato = texto(prato, "idMeal").ifBlank { idPrato },
                nome = texto(prato, "strMeal"),
                categoria = texto(prato, "strCategory"),
                origem = texto(prato, "strArea"),
                ingredientes = lerIngredientes(prato),
                instrucoes = texto(prato, "strInstructions").replace("\r", ""),
                dataHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())
            )
        } finally {
            conexao.disconnect()
        }
    }

    /**
     * A API traz os ingredientes em 20 pares de campos (strIngredient1..20 e
     * strMeasure1..20); os não usados vêm vazios ou null.
     * Resultado: "3/4 cup soy sauce\n1/2 cup water\n..."
     */
    private fun lerIngredientes(prato: JSONObject): String {
        val linhas = mutableListOf<String>()
        for (i in 1..20) {
            val ingrediente = texto(prato, "strIngredient$i")
            if (ingrediente.isBlank()) continue
            val medida = texto(prato, "strMeasure$i")
            linhas.add(if (medida.isBlank()) ingrediente else "$medida $ingrediente")
        }
        return linhas.joinToString("\n")
    }

    /** Lê um campo de texto tratando null do JSON como vazio. */
    private fun texto(json: JSONObject, campo: String): String =
        if (json.isNull(campo)) "" else json.optString(campo).trim()
}
