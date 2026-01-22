package it.sapienza.forestanimalsgame.data.remote.api

import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object ApiClient {
    private const val BASE_URL = "https://<VOSTRO_SERVER>/"

    val profileApi: ProfileApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(ProfileApi::class.java)
    }
}
