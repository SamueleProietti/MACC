package it.sapienza.forestanimalsgame.domain.repository

import android.graphics.Bitmap
import android.location.Location

interface ProfileRepository {
    suspend fun completeProfile(uid: String, location: Location, photo: Bitmap)
}
