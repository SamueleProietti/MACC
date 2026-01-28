package it.sapienza.forestanimalsgame.ui.lobby

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import it.sapienza.forestanimalsgame.data.model.ChatMessage
import it.sapienza.forestanimalsgame.data.model.Session
import it.sapienza.forestanimalsgame.data.repository.LobbyRepositoryImpl
import it.sapienza.forestanimalsgame.domain.repository.LobbyRepository
import kotlinx.coroutines.launch

class LobbyViewModel(
    private val repo: LobbyRepository = LobbyRepositoryImpl()
) : ViewModel() {

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

    private var removeSessionListener: (() -> Unit)? = null
    private var removeMessagesListener: (() -> Unit)? = null

    private fun currentUid(): String? = FirebaseAuth.getInstance().currentUser?.uid
    private fun currentName(): String =
        FirebaseAuth.getInstance().currentUser?.displayName ?: "Player"

    fun createSession() {
        val uid = currentUid()
        if (uid == null) { _error.value = "Utente non autenticato"; return }

        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val id = repo.createSession(uid, currentName())
                attachToSession(id)
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Errore creazione sessione"
            } finally {
                _loading.value = false
            }
        }
    }

    fun joinSession(id: String) {
        val uid = currentUid()
        if (uid == null) { _error.value = "Utente non autenticato"; return }
        if (id.isBlank()) { _error.value = "Inserisci un codice valido"; return }

        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val ok = repo.joinSession(id.trim(), uid, currentName())
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

        removeSessionListener = repo.listenSession(id) { s -> _session.postValue(s) }
        removeMessagesListener = repo.listenMessages(id) { msgs -> _messages.postValue(msgs) }
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
        val uid = currentUid()
        val sid = _sessionId.value
        if (uid == null || sid == null) return

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
            }
        }
    }

    override fun onCleared() {
        removeSessionListener?.invoke()
        removeMessagesListener?.invoke()
        super.onCleared()
    }
}
