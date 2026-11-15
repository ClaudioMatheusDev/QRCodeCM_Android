package com.example.qrcodeapp.util

import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder

/**
 * Regras para montar e validar os links do cardápio.
 *
 * Usa apenas java.net (e não android.net.Uri) para que possa ser testado com
 * testes unitários comuns (JUnit), sem emulador.
 */
object CardapioLinks {

    private val ESQUEMA_WEB = Regex("^(https?)://", RegexOption.IGNORE_CASE)

    /**
     * Garante que o link tenha esquema http/https em minúsculas:
     *   "meurestaurante.com"         -> "https://meurestaurante.com"
     *   "HTTP://meurestaurante.com"  -> "http://meurestaurante.com"
     */
    fun normalizar(link: String): String {
        val texto = link.trim()
        val esquema = ESQUEMA_WEB.find(texto)
        return if (esquema != null) {
            esquema.groupValues[1].lowercase() + "://" + texto.substring(esquema.range.last + 1)
        } else {
            "https://$texto"
        }
    }

    /** O link (depois de normalizado) é um endereço web com domínio válido? */
    fun linkValido(link: String): Boolean {
        if (link.isBlank()) return false
        return try {
            val uri = URI(normalizar(link))
            val host = uri.host.orEmpty()
            (uri.scheme == "http" || uri.scheme == "https") &&
                host.contains('.') && !host.startsWith('.') && !host.endsWith('.')
        } catch (e: URISyntaxException) {
            false
        }
    }

    /**
     * Junta o link do restaurante com o número da mesa:
     *   "meurestaurante.com/cardapio"        + "12" -> "https://meurestaurante.com/cardapio?mesa=12"
     *   "meurestaurante.com/cardapio?lang=pt" + "12" -> "https://meurestaurante.com/cardapio?lang=pt&mesa=12"
     *   "meurestaurante.com/cardapio#topo"   + "12" -> "https://meurestaurante.com/cardapio?mesa=12#topo"
     *
     * O parâmetro vai sempre antes do fragmento (#...), senão o servidor não o recebe,
     * e o valor da mesa é codificado para URL.
     */
    fun montarUrlMesa(linkBase: String, mesa: String): String {
        val url = normalizar(linkBase)

        val indiceFragmento = url.indexOf('#')
        val semFragmento = if (indiceFragmento >= 0) url.substring(0, indiceFragmento) else url
        val fragmento = if (indiceFragmento >= 0) url.substring(indiceFragmento) else ""

        val base = if (semFragmento.contains('?')) semFragmento else semFragmento.trimEnd('/')
        val separador = when {
            !base.contains('?') -> "?"
            base.endsWith("?") || base.endsWith("&") -> ""
            else -> "&"
        }
        val mesaCodificada = URLEncoder.encode(mesa.trim(), "UTF-8")
        return "$base${separador}mesa=$mesaCodificada$fragmento"
    }

    /** O texto lido de um QR é um link que pode ser aberto no navegador? */
    fun ehLinkWeb(texto: String): Boolean {
        val link = texto.trim()
        return ESQUEMA_WEB.containsMatchIn(link) && linkValido(link)
    }
}
