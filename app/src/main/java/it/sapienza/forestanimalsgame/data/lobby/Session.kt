package it.sapienza.forestanimalsgame.data.model

data class Session(
    val hostUid: String = "",
    val status: String = "LOBBY", // LOBBY | IN_GAME | FINISHED
    val members: List<Member> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),

    // aggiunto per Firestore (se nel documento lo scrivi quando premi Start)
    val startedAt: Long? = null
)
