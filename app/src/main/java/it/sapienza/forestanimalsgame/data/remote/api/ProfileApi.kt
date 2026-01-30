package it.sapienza.forestanimalsgame.data.remote.api

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part

// ✅ FIX: Aggiunto @SerializedName per forzare i nomi corretti nel JSON
data class ProfileMeUpsertRequest(
    @SerializedName("nickname") val nickname: String? = null,
    @SerializedName("lat") val lat: Double? = null,
    @SerializedName("lng") val lng: Double? = null,
    @SerializedName("photoUrl") val photoUrl: String? = null,
    @SerializedName("avatarId") val avatarId: String? = null
)

data class PhotoUploadResponse(
    @SerializedName("photoUrl") val photoUrl: String
)

data class ProfileMeResponse(
    val uid: String,
    val nickname: String? = null,
    @SerializedName("photoUrl") val photoUrl: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerializedName("avatarId") val avatarId: String? = null
)

interface ProfileApi {

    @GET("v1/profile/me")
    suspend fun getProfileMe(
        @Header("Authorization") bearer: String
    ): ProfileMeResponse

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