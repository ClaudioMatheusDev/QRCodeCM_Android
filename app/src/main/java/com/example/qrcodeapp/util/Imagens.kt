package com.example.qrcodeapp.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.roundToInt

object Imagens {

    /**
     * Lê uma foto do disco já reduzida, de forma que o maior lado tenha no máximo
     * [ladoMaximo] pixels.
     *
     * Fotos de câmeras atuais (48 MP, 108 MP, 200 MP...) ocupariam centenas de MB
     * se fossem decodificadas em resolução total, causando OutOfMemoryError.
     * Para ler um QR Code, ~1280 px é mais do que suficiente.
     *
     * Retorna null se o arquivo não for uma imagem válida.
     */
    fun decodificarReduzido(arquivo: File, ladoMaximo: Int): Bitmap? {
        // 1ª passada: só lê as dimensões, sem alocar a imagem
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(arquivo.absolutePath, limites)
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return null

        // inSampleSize em potências de 2: reduz já na decodificação (barato em memória)
        var amostra = 1
        while (maxOf(limites.outWidth, limites.outHeight) / (amostra * 2) >= ladoMaximo) {
            amostra *= 2
        }
        val opcoes = BitmapFactory.Options().apply { inSampleSize = amostra }
        val bitmap = BitmapFactory.decodeFile(arquivo.absolutePath, opcoes) ?: return null

        // Ajuste fino até o tamanho exato
        val maiorLado = maxOf(bitmap.width, bitmap.height)
        if (maiorLado <= ladoMaximo) return bitmap

        val escala = ladoMaximo.toFloat() / maiorLado
        val reduzido = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * escala).roundToInt().coerceAtLeast(1),
            (bitmap.height * escala).roundToInt().coerceAtLeast(1),
            true
        )
        if (reduzido !== bitmap) bitmap.recycle()
        return reduzido
    }
}
