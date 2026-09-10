package com.example.qrcodeapp.api

import com.example.qrcodeapp.model.QrReadResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Documentação da API: https://goqr.me/api/
 *
 * A mesma API (QR Server / goqr.me) oferece os dois recursos pedidos:
 *  - create-qr-code -> GERA a imagem do QR Code (GET, retorna PNG)
 *  - read-qr-code   -> LÊ / decodifica um QR Code a partir de uma imagem (POST multipart)
 */
interface QrApiService {

    /**
     * Gera uma imagem de QR Code a partir de um texto.
     * Retorna os bytes crus (image/png) via ResponseBody, exatamente como
     * fazemos com o JSON no exemplo do ViaCEP, só que aqui o corpo é uma imagem.
     */
    @Streaming
    @GET("create-qr-code/")
    suspend fun gerarQrCode(
        @Query("size") tamanho: String = "300x300",
        @Query("data") dado: String,
        @Query("ecc") correcaoErro: String = "M"
    ): Response<ResponseBody>

    /**
     * Envia uma foto (multipart/form-data) para a API decodificar o QR Code
     * presente na imagem.
     */
    @Multipart
    @POST("read-qr-code/")
    suspend fun lerQrCode(
        @Part imagem: MultipartBody.Part,
        @Query("outputformat") formatoSaida: String = "json"
    ): Response<List<QrReadResponse>>
}
