package it.sapienza.forestanimalsgame.domain.repository

import it.sapienza.forestanimalsgame.data.model.ChatMessage
import it.sapienza.forestanimalsgame.data.model.GameState
import it.sapienza.forestanimalsgame.data.model.Session

interface LobbyRepository {

    // avatarId salvato nel Member della sessione
    suspend fun createSession(hostUid: String, hostName: String, hostAvatarId: String): String
    suspend fun joinSession(sessionId: String, uid: String, name: String, avatarId: String): Boolean
    suspend fun leaveSession(sessionId: String, uid: String)

    fun listenSession(sessionId: String, onUpdate: (Session?) -> Unit): () -> Unit
    fun listenMessages(sessionId: String, onUpdate: (List<ChatMessage>) -> Unit): () -> Unit
    suspend fun sendMessage(sessionId: String, msg: ChatMessage)

    suspend fun startGameIfHost(sessionId: String)

    // RESUME session
    suspend fun findActiveSessionForUser(uid: String): String?

    // SAVE / LOAD game snapshot
    suspend fun loadGameState(sessionId: String, uid: String): GameState?
    suspend fun saveGameState(sessionId: String, uid: String, state: GameState)
    suspend fun finishSessionIfIdle(sessionId: String, idleTimeoutMs: Long): Boolean

    // Ascolta TUTTI gli stati dei giocatori nella sessione (per unire le chiavi raccolte)
    fun listenGameStates(sessionId: String, onUpdate: (List<GameState>) -> Unit): () -> Unit

    //weather api
    suspend fun fetchWeather(backendUrl: String, lat: Double, lng: Double): String

}


