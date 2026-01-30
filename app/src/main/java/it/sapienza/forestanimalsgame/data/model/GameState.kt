package it.sapienza.forestanimalsgame.data.model

data class GameState(
    val avatarX: Double = 0.0,
    val avatarY: Double = 0.0,
    val targetX: Double = 0.0,
    val targetY: Double = 0.0,

    val activeQuestId: String? = null,
    val completed: List<String> = emptyList(),

    val panX: Double = 0.0,
    val panY: Double = 0.0,
    val zoom: Double = 1.0,

    // ✅ "ultimo aggiornamento vince"
    val updatedAt: Long = System.currentTimeMillis()
)
