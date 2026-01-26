package it.sapienza.forestanimalsgame.data.repository

import android.graphics.Bitmap
import android.location.Location
import com.google.firebase.auth.FirebaseAuth
import it.sapienza.forestanimalsgame.data.remote.api.ApiClient
import it.sapienza.forestanimalsgame.data.remote.api.ProfileApi
import it.sapienza.forestanimalsgame.data.remote.api.ProfileMeUpsertRequest
import it.sapienza.forestanimalsgame.domain.repository.ProfileRepository
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.ByteArrayOutputStream

class ProfileRepositoryImpl(
    private val api: ProfileApi = ApiClient.profileApi
) : ProfileRepository {

    override suspend fun completeProfile(uid: String, location: Location, photo: Bitmap) {
        // uid arriva dall’interfaccia, ma lato backend l’uid vero lo ricava dal token
        val bearer = getBearerToken()

        // 1) upload foto -> backend -> GCS
        val photoUrl = uploadPhotoToBackend(bearer, photo)

        // 2) upsert profilo -> backend -> Cloud SQL
        val req = ProfileMeUpsertRequest(
            nickname = null,
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

        val mediaType = MediaType.parse("image/jpeg")
        val requestBody = RequestBody.create(mediaType, bytes)

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
