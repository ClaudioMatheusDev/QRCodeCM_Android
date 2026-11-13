package com.example.qrcodeapp.util

/** Reconhece o código de um prato (ID do TheMealDB) no texto lido de um QR Code. */
object Prato {

    // Só números (ex.: "52772"), com prefixo opcional: "prato:52772", "Prato 52772", "PRATO#52772"
    private val PADRAO = Regex("""^(?:prato\s*[:#-]?\s*)?(\d{4,6})$""", RegexOption.IGNORE_CASE)

    /**
     * Devolve o ID do prato ou null se o texto não for um código de prato.
     *   "52772"        -> "52772"
     *   "prato:52772"  -> "52772"
     *   "Mesa 12"      -> null
     */
    fun extrairId(texto: String): String? =
        PADRAO.find(texto.trim())?.groupValues?.get(1)
}
