package it.sapienza.forestanimalsgame.data.remote.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // Esempio: "https://forestanimal-api-xxxxx-ew.a.run.app/"
    private const val BASE_URL = "https://forestanimal-api-1002662831596.europe-west12.run.app/"

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create()) // ✅ QUESTO
            .build()
    }

    val profileApi: ProfileApi by lazy {
        retrofit.create(ProfileApi::class.java)
    }

}
