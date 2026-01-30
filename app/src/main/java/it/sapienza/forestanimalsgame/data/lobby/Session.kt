package it.sapienza.forestanimalsgame.data.model

data class Session(
    val hostUid: String = "",
    val status: String = "LOBBY", // LOBBY | IN_GAME | FINISHED
    val members: List<Member> = emptyList(),

    // ✅ NEW: array semplice interrogabile per resume
    val memberUids: List<String> = emptyList(),

    val createdAt: Long = System.currentTimeMillis(),

    // già presente
    val startedAt: Long? = null,

    // ✅ NEW: utile per ordinare lato client (se vuoi)
    val updatedAt: Long = System.currentTimeMillis()
)
