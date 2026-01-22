package it.sapienza.forestanimalsgame.data.remote.api

import retrofit2.http.Body
import retrofit2.http.POST

data class ProfileUpsertRequest(
    val uid: String,
    val lat: Double,
    val lng: Double,
    val photoUrl: String
)

interface ProfileApi {
    @POST("profiles/upsert")
    suspend fun upsertProfile(@Body req: ProfileUpsertRequest)
}
