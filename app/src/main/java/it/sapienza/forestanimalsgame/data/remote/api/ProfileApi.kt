package it.sapienza.forestanimalsgame.data.remote.api

import okhttp3.MultipartBody
import retrofit2.http.*

data class ProfileMeUpsertRequest(
    val nickname: String? = null,
    val lat: Double,
    val lng: Double,
    val photoUrl: String? = null
)

data class PhotoUploadResponse(
    val photoUrl: String
)

interface ProfileApi {

    @Multipart
    @POST("v1/profile/me/photo")
    suspend fun uploadProfilePhoto(
        @Header("Authorization") bearer: String,
        @Part photo: MultipartBody.Part
    ): PhotoUploadResponse

    @PUT("v1/profile/me")
    suspend fun upsertProfileMe(
        @Header("Authorization") bearer: String,
        @Body req: ProfileMeUpsertRequest
    )
}
