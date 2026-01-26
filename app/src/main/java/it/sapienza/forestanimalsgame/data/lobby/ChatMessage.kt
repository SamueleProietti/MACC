package it.sapienza.forestanimalsgame.data.model

data class ChatMessage(
    val senderUid: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
