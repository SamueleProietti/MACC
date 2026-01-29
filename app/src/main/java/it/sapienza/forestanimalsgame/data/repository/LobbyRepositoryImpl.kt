package it.sapienza.forestanimalsgame.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import it.sapienza.forestanimalsgame.data.model.ChatMessage
import it.sapienza.forestanimalsgame.data.model.Member
import it.sapienza.forestanimalsgame.data.model.Session
import it.sapienza.forestanimalsgame.domain.repository.LobbyRepository
import it.sapienza.forestanimalsgame.data.remote.api.ProfileMeResponse


class LobbyRepositoryImpl(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : LobbyRepository {

    private val sessions = db.collection("sessions")

    override suspend fun createSession(hostUid: String, hostName: String): String {
        val member = Member(uid = hostUid, displayName = hostName)
        val session = Session(
            hostUid = hostUid,
            status = "LOBBY",
            members = listOf(member),
            createdAt = System.currentTimeMillis()
        )
        val docRef = sessions.add(session).await()
        return docRef.id
    }

    override suspend fun joinSession(sessionId: String, uid: String, name: String): Boolean {
        val docRef = sessions.document(sessionId)

        return db.runTransaction { tx ->
            val snap = tx.get(docRef)
            if (!snap.exists()) return@runTransaction false

            val session = snap.toObject(Session::class.java) ?: return@runTransaction false
            val members = session.members.toMutableList()

            // già dentro?
            if (members.any { it.uid == uid }) return@runTransaction true

            // limite pragmatico
            if (members.size >= 4) return@runTransaction false

            members.add(Member(uid = uid, displayName = name))
            tx.set(docRef, session.copy(members = members))
            true
        }.await()
    }

    override suspend fun leaveSession(sessionId: String, uid: String) {
        val docRef = sessions.document(sessionId)

        db.runTransaction { tx ->
            val snap = tx.get(docRef)
            if (!snap.exists()) return@runTransaction

            val session = snap.toObject(Session::class.java) ?: return@runTransaction
            val newMembers = session.members.filterNot { it.uid == uid }

            if (newMembers.isEmpty()) {
                tx.delete(docRef)
            } else {
                val newHost = if (session.hostUid == uid) newMembers.first().uid else session.hostUid
                tx.set(docRef, session.copy(hostUid = newHost, members = newMembers))
            }
        }.await()
    }

    override fun listenSession(sessionId: String, onUpdate: (Session?) -> Unit): () -> Unit {
        val reg = sessions.document(sessionId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onUpdate(null)
                    return@addSnapshotListener
                }
                onUpdate(snap?.toObject(Session::class.java))
            }
        return { reg.remove() }
    }

    override fun listenMessages(sessionId: String, onUpdate: (List<ChatMessage>) -> Unit): () -> Unit {
        val reg = sessions.document(sessionId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limit(50)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }
                val msgs = snap?.documents?.mapNotNull { it.toObject(ChatMessage::class.java) } ?: emptyList()
                onUpdate(msgs)
            }
        return { reg.remove() }
    }

    override suspend fun sendMessage(sessionId: String, msg: ChatMessage) {
        sessions.document(sessionId)
            .collection("messages")
            .add(msg)
            .await()
    }

    override suspend fun startGameIfHost(sessionId: String) {
        val currentUid = auth.currentUser?.uid ?: throw IllegalStateException("not_authenticated")
        val ref = sessions.document(sessionId)

        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val sess = snap.toObject(Session::class.java) ?: throw IllegalStateException("session_not_found")

            if (sess.hostUid != currentUid) throw IllegalStateException("only_host_can_start")
            if (sess.status != "LOBBY") throw IllegalStateException("session_not_in_lobby")
            if (sess.members.size < 1) throw IllegalStateException("not_enough_players")

            tx.update(ref, mapOf(
                "status" to "IN_GAME",
                "startedAt" to System.currentTimeMillis()
            ))
            null
        }.await()
    }
}
