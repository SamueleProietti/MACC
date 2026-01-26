package it.sapienza.forestanimalsgame.data.remote.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // Esempio: "https://forestanimal-api-xxxxx-ew.a.run.app/"
    private const val BASE_URL = "https://forestanimal-api-1002662831596.europe-west12.run.app/"

    private val okHttp by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val profileApi: ProfileApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(ProfileApi::class.java)
    }
}
