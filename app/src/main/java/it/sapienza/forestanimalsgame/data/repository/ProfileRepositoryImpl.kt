package it.sapienza.forestanimalsgame.data.repository

import android.graphics.Bitmap
import android.location.Location
import com.google.firebase.auth.FirebaseAuth
import it.sapienza.forestanimalsgame.data.remote.api.ApiClient
import it.sapienza.forestanimalsgame.data.remote.api.ProfileApi
import it.sapienza.forestanimalsgame.data.remote.api.ProfileMeResponse
import it.sapienza.forestanimalsgame.data.remote.api.ProfileMeUpsertRequest
import it.sapienza.forestanimalsgame.domain.repository.ProfileRepository
import kotlinx.coroutines.tasks.await
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

class ProfileRepositoryImpl(
    private val api: ProfileApi = ApiClient.profileApi
) : ProfileRepository {

    override suspend fun getMyProfile(): ProfileMeResponse {
        val bearer = getBearerToken()
        return api.getProfileMe(bearer)
    }

    override suspend fun uploadMyPhoto(photo: Bitmap): String {
        val bearer = getBearerToken()
        return uploadPhotoToBackend(bearer, photo)
    }

    override suspend fun upsertMyProfile(location: Location?, photoUrl: String?, avatarId: String?) {
        val bearer = getBearerToken()
        val req = ProfileMeUpsertRequest(
            nickname = null,
            lat = location?.latitude,
            lng = location?.longitude,
            photoUrl = photoUrl,
            avatarId = avatarId
        )
        api.upsertProfileMe(bearer, req)
    }

    private suspend fun uploadPhotoToBackend(bearer: String, bitmap: Bitmap): String {
        val bytes = ByteArrayOutputStream().use { baos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            baos.toByteArray()
        }

        val mediaType = "image/jpeg".toMediaType()
        val requestBody = bytes.toRequestBody(mediaType)

        val part = MultipartBody.Part.createFormData(
            "photo",
            "profile.jpg",
            requestBody
        )

        return api.uploadProfilePhoto(bearer, part).photoUrl
    }

    private suspend fun getBearerToken(): String {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("Utente non autenticato")

        val token = user.getIdToken(false).await().token
            ?: throw IllegalStateException("Token Firebase nullo")

        return "Bearer $token"
    }
}
