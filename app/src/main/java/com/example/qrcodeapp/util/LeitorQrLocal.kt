package com.example.qrcodeapp.util

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * Decodifica um QR Code no próprio aparelho (ZXing), sem internet.
 * Usado como reserva quando a API de leitura não responde corretamente.
 */
object LeitorQrLocal {

    private val dicas = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
    )

    /** Retorna o texto do QR, ou null se nenhum QR for encontrado na imagem. */
    fun decodificar(bitmap: Bitmap): String? {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val fonte = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)

        // Tenta a imagem normal e, se falhar, invertida (QR claro em fundo escuro)
        return tentar(fonte) ?: tentar(fonte.invert())
    }

    private fun tentar(fonte: com.google.zxing.LuminanceSource): String? = try {
        QRCodeReader()
            .decode(BinaryBitmap(HybridBinarizer(fonte)), dicas)
            .text
            ?.takeIf { it.isNotBlank() }
    } catch (e: NotFoundException) {
        null
    } catch (e: ReaderException) {
        null
    }
}
