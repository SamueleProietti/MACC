package it.sapienza.forestanimalsgame.domain.repository

import it.sapienza.forestanimalsgame.data.model.ChatMessage
import it.sapienza.forestanimalsgame.data.model.Session

interface LobbyRepository {
    suspend fun createSession(hostUid: String, hostName: String): String
    suspend fun joinSession(sessionId: String, uid: String, name: String): Boolean
    suspend fun leaveSession(sessionId: String, uid: String)

    fun listenSession(sessionId: String, onUpdate: (Session?) -> Unit): () -> Unit
    fun listenMessages(sessionId: String, onUpdate: (List<ChatMessage>) -> Unit): () -> Unit
    suspend fun sendMessage(sessionId: String, msg: ChatMessage)

    suspend fun startGame(sessionId: String, hostUid: String)
}
