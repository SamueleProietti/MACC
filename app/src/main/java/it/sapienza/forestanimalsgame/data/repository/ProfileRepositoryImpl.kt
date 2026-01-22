package it.sapienza.forestanimalsgame.data.repository

import android.graphics.Bitmap
import android.location.Location
import com.google.firebase.storage.FirebaseStorage
import it.sapienza.forestanimalsgame.data.remote.api.ApiClient
import it.sapienza.forestanimalsgame.data.remote.api.ProfileApi
import it.sapienza.forestanimalsgame.data.remote.api.ProfileUpsertRequest
import it.sapienza.forestanimalsgame.domain.repository.ProfileRepository
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

class ProfileRepositoryImpl(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val api: ProfileApi = ApiClient.profileApi
) : ProfileRepository {

    override suspend fun completeProfile(uid: String, location: Location, photo: Bitmap) {
        val photoUrl = uploadPhoto(uid, photo)

        val req = ProfileUpsertRequest(
            uid = uid,
            lat = location.latitude,
            lng = location.longitude,
            photoUrl = photoUrl
        )
        api.upsertProfile(req)
    }

    private suspend fun uploadPhoto(uid: String, bitmap: Bitmap): String {
        val bytes = ByteArrayOutputStream().use { baos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            baos.toByteArray()
        }

        val ref = storage.reference.child("users/$uid/profile.jpg")
        ref.putBytes(bytes).await()
        return ref.downloadUrl.await().toString()
    }
}
