package it.sapienza.forestanimalsgame.data.repository

import android.graphics.Bitmap
import android.location.Location
import com.google.firebase.auth.FirebaseAuth
import it.sapienza.forestanimalsgame.data.remote.api.ApiClient
import it.sapienza.forestanimalsgame.data.remote.api.ProfileApi
import it.sapienza.forestanimalsgame.data.remote.api.ProfileMeUpsertRequest
import it.sapienza.forestanimalsgame.domain.repository.ProfileRepository
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

class ProfileRepositoryImpl(
    private val api: ProfileApi = ApiClient.profileApi
) : ProfileRepository {

    override suspend fun completeProfile(uid: String, location: Location, photo: Bitmap) {
        val bearer = getBearerToken()

        // 1) upload foto profilo -> backend -> GCS
        val photoUrl = uploadPhotoToBackend(bearer, photo)

        // 2) upsert profilo (lat/lng + photoUrl)
        val req = ProfileMeUpsertRequest(
            nickname = null, // al momento non lo chiedete in UI
            lat = location.latitude,
            lng = location.longitude,
            photoUrl = photoUrl
        )
        api.upsertProfileMe(bearer, req)
    }

    private suspend fun uploadPhotoToBackend(bearer: String, bitmap: Bitmap): String {
        val bytes = ByteArrayOutputStream().use { baos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            baos.toByteArray()
        }

        val body = bytes.toRequestBody("image/jpeg".toMediaType())
        val part = MultipartBody.Part.createFormData(
            name = "photo",
            filename = "profile.jpg",
            body = body
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
