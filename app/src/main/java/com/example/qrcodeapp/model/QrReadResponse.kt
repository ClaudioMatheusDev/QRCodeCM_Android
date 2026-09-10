package com.example.qrcodeapp.model

/**
 * Representa a resposta JSON do endpoint "read-qr-code" da API QR Server (goqr.me).
 *
 * Formato retornado pela API (é sempre uma LISTA, pois uma mesma imagem
 * pode conter mais de um QR Code):
 *
 * [
 *   {
 *     "type": "qrcode",
 *     "symbol": [
 *       { "seq": 0, "data": "texto decodificado", "error": null }
 *     ]
 *   }
 * ]
 */
data class QrReadResponse(
    val type: String?,
    val symbol: List<QrSymbol>?
)

data class QrSymbol(
    val seq: Int?,
    val data: String?,
    val error: String?
)
