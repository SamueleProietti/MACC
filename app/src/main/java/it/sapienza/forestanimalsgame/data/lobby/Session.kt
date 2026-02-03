package it.sapienza.forestanimalsgame.data.model

data class Session(
    val hostUid: String = "",
    val status: String = "LOBBY", // LOBBY | IN_GAME | FINISHED
    val members: List<Member> = emptyList(),

    // array semplice interrogabile per resume
    val memberUids: List<String> = emptyList(),

    val createdAt: Long = System.currentTimeMillis(),

    val startedAt: Long? = null,

    val updatedAt: Long = System.currentTimeMillis()
)
