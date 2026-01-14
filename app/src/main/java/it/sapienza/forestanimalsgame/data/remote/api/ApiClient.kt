object ApiClient {
    private const val BASE_URL = "https://<VOSTRO_SERVER>/"

    val profileApi: ProfileApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(ProfileApi::class.java)
    }
}
