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
