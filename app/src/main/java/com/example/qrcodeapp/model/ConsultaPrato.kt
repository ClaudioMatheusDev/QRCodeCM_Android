package com.example.qrcodeapp.model

/**
 * MODEL: uma consulta de prato bem-sucedida, como fica gravada na tabela
 * "historico" do SQLite.
 */
data class ConsultaPrato(
    val id: Long = 0,
    val qrCode: String,        // texto original lido do QR Code
    val idPrato: String,       // ID do prato no TheMealDB (ex.: 52772)
    val nome: String,
    val categoria: String,
    val origem: String,        // culinária de origem (ex.: Japanese, Italian)
    val ingredientes: String,  // um ingrediente por linha, com a quantidade
    val instrucoes: String,    // modo de preparo
    val dataHora: String       // quando a consulta foi feita (dd/MM/yyyy HH:mm)
) {
    /** Texto curto do resultado, usado na tela principal. */
    fun resumo(): String = listOf(categoria, origem).filter { it.isNotBlank() }.joinToString(" · ")

    /** O ArrayAdapter usa toString() para mostrar cada linha da ListView. */
    override fun toString(): String = "$nome\n$categoria · $dataHora"
}
