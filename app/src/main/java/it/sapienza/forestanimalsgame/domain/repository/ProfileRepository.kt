package it.sapienza.forestanimalsgame.domain.repository

import android.graphics.Bitmap
import android.location.Location
import it.sapienza.forestanimalsgame.data.remote.api.ProfileMeResponse

interface ProfileRepository {
    suspend fun getMyProfile(): ProfileMeResponse
    suspend fun uploadMyPhoto(photo: Bitmap): String
    suspend fun upsertMyProfile(location: Location?, photoUrl: String?, avatarId: String?)
}
