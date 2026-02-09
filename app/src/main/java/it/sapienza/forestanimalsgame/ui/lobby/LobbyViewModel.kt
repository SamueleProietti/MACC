package it.sapienza.forestanimalsgame.ui.lobby

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import it.sapienza.forestanimalsgame.data.model.ChatMessage
import it.sapienza.forestanimalsgame.data.model.GameState
import it.sapienza.forestanimalsgame.data.model.Session
import it.sapienza.forestanimalsgame.data.repository.LobbyRepositoryImpl
import it.sapienza.forestanimalsgame.data.repository.ProfileRepositoryImpl
import it.sapienza.forestanimalsgame.domain.repository.LobbyRepository
import it.sapienza.forestanimalsgame.domain.repository.ProfileRepository
import kotlinx.coroutines.launch
import android.util.Log

class LobbyViewModel(
    private val repo: LobbyRepository = LobbyRepositoryImpl(),
    private val profileRepo: ProfileRepository = ProfileRepositoryImpl()
) : ViewModel() {

    private val BACKEND_URL = "https://forestanimal-api-1002662831596.europe-west12.run.app"
    private val _sessionId = MutableLiveData<String?>(null)
    val sessionId: LiveData<String?> = _sessionId

    private val _session = MutableLiveData<Session?>(null)
    val session: LiveData<Session?> = _session

    private val _messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = _messages

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _avatarId = MutableLiveData("fox")
    val avatarId: LiveData<String> = _avatarId

    // stato di gioco caricato da Firestore (snapshot)
    private val _gameState = MutableLiveData<GameState?>(null)
    val gameState: LiveData<GameState?> = _gameState

    private val _gameStateLoaded = MutableLiveData(false)
    val gameStateLoaded: LiveData<Boolean> = _gameStateLoaded

    private val _weatherCondition = MutableLiveData("clear")
    val weatherCondition: LiveData<String> = _weatherCondition

    private var removeSessionListener: (() -> Unit)? = null
    private var removeMessagesListener: (() -> Unit)? = null

    // TIMEOUT anti-zombie
    private val IDLE_TIMEOUT_MS = 10 * 60 * 1000L     // 10 minuti
    private val IDLE_CHECK_EVERY_MS = 30 * 1000L      // check ogni 30s
    private var idleJob: kotlinx.coroutines.Job? = null

    // Variabile per gestire l'ascolto continuo
    private var removeGameStateListener: (() -> Unit)? = null


    private fun currentUid(): String? = FirebaseAuth.getInstance().currentUser?.uid
    private fun currentName(): String = FirebaseAuth.getInstance().currentUser?.displayName ?: "Player"

    fun loadMyAvatarId() {
        viewModelScope.launch {
            try {
                val prof = profileRepo.getMyProfile()
                val id = prof.avatarId
                _avatarId.value = if (id.isNullOrBlank()) "fox" else id
            } catch (_: Exception) {
                _avatarId.value = _avatarId.value ?: "fox"
            }
        }
    }

    // RESUME: cerca sessione attiva e si attacca
    fun resumeMyActiveSession() {
        val uid = currentUid() ?: return

        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val sid = repo.findActiveSessionForUser(uid)
                if (!sid.isNullOrBlank()) {
                    attachToSession(sid)
                }
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Session resume error"
            } finally {
                _loading.value = false
            }
        }
    }

    fun createSession() {
        val uid = currentUid()
        if (uid == null) { _error.value = "User not authenticated"; return }

        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val id = repo.createSession(uid, currentName(), _avatarId.value ?: "fox")
                attachToSession(id)
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Session creation error"
            } finally {
                _loading.value = false
            }
        }
    }

    fun joinSession(id: String) {
        val uid = currentUid()
        if (uid == null) { _error.value = "User not authenticated"; return }
        if (id.isBlank()) { _error.value = "Insert a valid code"; return }

        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val ok = repo.joinSession(id.trim(), uid, currentName(), _avatarId.value ?: "fox")
                if (!ok) {
                    _error.value = "Impossibile entrare: sessione non trovata o piena"
                } else {
                    attachToSession(id.trim())
                }
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Errore join sessione"
            } finally {
                _loading.value = false
            }
        }
    }

    private fun attachToSession(id: String) {
        _sessionId.value = id

        removeSessionListener?.invoke()
        removeMessagesListener?.invoke()

        removeSessionListener = repo.listenSession(id) { s ->
            _session.postValue(s)

            if (s == null || s.status == "FINISHED") {
                // sessione chiusa (timeout o altro): torna alla entry senza abbandono esplicito
                detachLocal()
            }
        }
        removeMessagesListener = repo.listenMessages(id) { msgs -> _messages.postValue(msgs) }

        startIdleWatcher(id)
    }

    fun sendMessage(text: String) {
        val uid = currentUid()
        val sid = _sessionId.value
        if (uid == null || sid == null) { _error.value = "Non sei in una sessione"; return }
        if (text.isBlank()) return

        viewModelScope.launch {
            try {
                repo.sendMessage(
                    sid,
                    ChatMessage(
                        senderUid = uid,
                        senderName = currentName(),
                        text = text.trim(),
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Errore invio messaggio"
            }
        }
    }

    fun startGameIfHost() {
        val sid = _sessionId.value ?: return
        viewModelScope.launch {
            try {
                repo.startGameIfHost(sid)
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Errore start game"
            }
        }
    }

    fun leaveSession() {
        val uid = currentUid()
        val sid = _sessionId.value
        if (uid == null || sid == null) return

        idleJob?.cancel()
        idleJob = null

        viewModelScope.launch {
            try {
                repo.leaveSession(sid, uid)
            } finally {
                removeSessionListener?.invoke()
                removeMessagesListener?.invoke()
                removeSessionListener = null
                removeMessagesListener = null
                _sessionId.value = null
                _session.value = null
                _messages.value = emptyList()
                _gameState.value = null
                _gameStateLoaded.value = false
            }
        }
    }

    // ---------------- GAME STATE ----------------

    fun loadMyGameState(sessionId: String) {
        val uid = currentUid() ?: return
        
        // Reset del flag prima di iniziare
        _gameStateLoaded.value = false 
        
        viewModelScope.launch {
            try {
                _gameState.value = repo.loadGameState(sessionId, uid)
            } catch (_: Exception) {
                _gameState.value = null
            } finally {
                // Segnala che il caricamento è finito (con o senza dati)
                _gameStateLoaded.value = true
            }
        }
    }

    fun loadRealWeather() {
        viewModelScope.launch {
            try {
                // 1. Recupera la posizione dal profilo salvato su Firebase
                val profile = profileRepo.getMyProfile()
                val lat = profile.lat
                val lng = profile.lng


                if (lat != null && lng != null) {
                    // 2. Chiama il tuo backend Python
                    val condition = repo.fetchWeather(BACKEND_URL, lat, lng)
                    _weatherCondition.postValue(condition)
                } else {
                    Log.d("VIEWMODEL", "Nessuna posizione nel profilo, uso meteo default")
                }
            } catch (e: Exception) {
                Log.e("VIEWMODEL", "Errore caricamento meteo", e)
            }
        }
    }

    fun saveMyGameState(sessionId: String, state: GameState) {
        val uid = currentUid() ?: return
        viewModelScope.launch {
            try {
                repo.saveGameState(sessionId, uid, state)
            } catch (_: Exception) {

            }
        }
    }

    override fun onCleared() {
        removeSessionListener?.invoke()
        removeMessagesListener?.invoke()
        removeGameStateListener?.invoke()

        idleJob?.cancel()
        idleJob = null

        super.onCleared()
    }

    private fun startIdleWatcher(sessionId: String) {
        idleJob?.cancel()
        idleJob = viewModelScope.launch {
            while (true) {
                try {
                    repo.finishSessionIfIdle(sessionId, IDLE_TIMEOUT_MS)
                } catch (_: Exception) {
                    // best-effort
                }
                kotlinx.coroutines.delay(IDLE_CHECK_EVERY_MS)
            }
        }
    }

    private fun detachLocal() {
        idleJob?.cancel()
        idleJob = null

        removeSessionListener?.invoke()
        removeMessagesListener?.invoke()
        removeSessionListener = null
        removeMessagesListener = null

        _sessionId.postValue(null)
        _session.postValue(null)
        _messages.postValue(emptyList())
        _gameState.postValue(null)
        _gameStateLoaded.postValue(false)
    }

    suspend fun saveMyGameStateNow(sessionId: String, state: it.sapienza.forestanimalsgame.data.model.GameState) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        repo.saveGameState(sessionId, uid, state)   // è suspend e fa await
    }

    fun listenToGameState(sessionId: String) {
        val uid = currentUid() ?: return

        removeGameStateListener?.invoke()

        removeGameStateListener = repo.listenGameStates(sessionId) { allPlayerStates ->

            val allCompletedMissions = allPlayerStates
                .flatMap { it.completed }
                .toSet()
                .toList()
                .sorted()

            val currentState = _gameState.value ?: GameState()


            _gameState.postValue(currentState.copy(completed = allCompletedMissions))
        }
    }



}
