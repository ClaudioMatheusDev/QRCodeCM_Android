package com.example.qrcodeapp.util

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.Binarizer
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlin.math.roundToInt

/**
 * Decodifica um QR Code no próprio aparelho (ZXing), sem internet.
 * Usado como reserva quando a API de leitura não responde corretamente.
 *
 * Fotos reais (principalmente de um QR exibido em tela de monitor/celular)
 * têm reflexo, ruído e "moiré" (listras). Uma única tentativa costuma falhar,
 * então tentamos combinações de:
 *  - escala: reduzir a imagem suaviza o ruído e o moiré;
 *  - binarizador: Hybrid (bom com luz irregular) e GlobalHistogram (bom com ruído fino);
 *  - imagem normal e invertida (QR claro em fundo escuro).
 */
object LeitorQrLocal {

    /** Maior lado (px) de cada tentativa; 0 = tamanho original. */
    private val ESCALAS = intArrayOf(0, 900, 640, 450)

    private val dicas = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
    )

    /** Retorna o texto do QR, ou null se nenhum QR for encontrado na imagem. */
    fun decodificar(bitmap: Bitmap): String? {
        val maiorLado = maxOf(bitmap.width, bitmap.height)
        for (escala in ESCALAS) {
            if (escala != 0 && escala >= maiorLado) continue
            val fonte = fonteLuminancia(bitmap, escala)
            for (candidata in listOf(fonte, fonte.invert())) {
                tentar(HybridBinarizer(candidata))?.let { return it }
                tentar(GlobalHistogramBinarizer(candidata))?.let { return it }
            }
        }
        return null
    }

    private fun fonteLuminancia(original: Bitmap, ladoMaximo: Int): LuminanceSource {
        val bitmap = if (ladoMaximo == 0) {
            original
        } else {
            val fator = ladoMaximo.toFloat() / maxOf(original.width, original.height)
            Bitmap.createScaledBitmap(
                original,
                (original.width * fator).roundToInt().coerceAtLeast(1),
                (original.height * fator).roundToInt().coerceAtLeast(1),
                true
            )
        }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val fonte = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        if (bitmap !== original) bitmap.recycle()
        return fonte
    }

    private fun tentar(binarizador: Binarizer): String? = try {
        QRCodeReader()
            .decode(BinaryBitmap(binarizador), dicas)
            .text
            ?.takeIf { it.isNotBlank() }
    } catch (e: ReaderException) {
        null
    }
}
