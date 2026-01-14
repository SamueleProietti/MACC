data class ProfileUpsertRequest(
    val uid: String,
    val lat: Double,
    val lng: Double,
    val photoUrl: String
)

interface ProfileApi {
    @POST("profiles/upsert")
    suspend fun upsertProfile(@Body req: ProfileUpsertRequest)
}
