interface ProfileRepository {
    suspend fun completeProfile(uid: String, location: Location, photo: Bitmap)
}
//POST al server per la foto 