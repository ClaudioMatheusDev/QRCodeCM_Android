package com.example.qrcodeapp.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Ponto único de criação do Retrofit, seguindo o mesmo padrão usado em aula
 * (OkHttp cuida das requisições HTTP; Retrofit + Gson mapeiam o JSON de/para objetos Kotlin).
 */
object RetrofitClient {

    private const val BASE_URL = "https://api.qrserver.com/v1/"

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    val api: QrApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(QrApiService::class.java)
    }
}
