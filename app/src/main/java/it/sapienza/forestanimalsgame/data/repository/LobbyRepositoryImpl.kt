package it.sapienza.forestanimalsgame.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import it.sapienza.forestanimalsgame.data.model.ChatMessage
import it.sapienza.forestanimalsgame.data.model.GameState
import it.sapienza.forestanimalsgame.data.model.Member
import it.sapienza.forestanimalsgame.data.model.Session
import it.sapienza.forestanimalsgame.domain.repository.LobbyRepository


class LobbyRepositoryImpl(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : LobbyRepository {

    private val sessions = db.collection("sessions")

    override suspend fun createSession(hostUid: String, hostName: String, hostAvatarId: String): String {
        val member = Member(uid = hostUid, displayName = hostName, avatar = hostAvatarId)
        val now = System.currentTimeMillis()
        val session = Session(
            hostUid = hostUid,
            status = "LOBBY",
            members = listOf(member),
            memberUids = listOf(hostUid),
            createdAt = now,
            updatedAt = now
        )
        val docRef = sessions.add(session).await()
        return docRef.id
    }

    override suspend fun joinSession(sessionId: String, uid: String, name: String, avatarId: String): Boolean {
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

            members.add(Member(uid = uid, displayName = name, avatar = avatarId))
            val newUids = members.map { it.uid }
            val now = System.currentTimeMillis()

            tx.set(
                docRef,
                session.copy(
                    members = members,
                    memberUids = newUids,
                    updatedAt = now
                )
            )
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
                val now = System.currentTimeMillis()
                tx.set(
                    docRef,
                    session.copy(
                        hostUid = newHost,
                        members = newMembers,
                        memberUids = newMembers.map { it.uid },
                        updatedAt = now
                    )
                )
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

        // opzionale: bump updatedAt session
        sessions.document(sessionId).update("updatedAt", System.currentTimeMillis()).await()
    }

    override suspend fun startGameIfHost(sessionId: String) {
        val currentUid = auth.currentUser?.uid ?: throw IllegalStateException("not_authenticated")
        val ref = sessions.document(sessionId)

        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val sess = snap.toObject(Session::class.java) ?: throw IllegalStateException("session_not_found")

            if (sess.hostUid != currentUid) throw IllegalStateException("only_host_can_start")
            if (sess.status != "LOBBY") throw IllegalStateException("session_not_in_lobby")
            if (sess.members.isEmpty()) throw IllegalStateException("not_enough_players")

            val now = System.currentTimeMillis()
            tx.update(
                ref,
                mapOf(
                    "status" to "IN_GAME",
                    "startedAt" to now,
                    "updatedAt" to now
                )
            )
            null
        }.await()
    }

    // ---------------- RESUME SESSION ----------------

    override suspend fun findActiveSessionForUser(uid: String): String? {
        // ✅ niente indici complessi: una sola whereArrayContains
        val snap = sessions
            .whereArrayContains("memberUids", uid)
            .limit(20)
            .get()
            .await()

        val candidates = snap.documents.mapNotNull { doc ->
            val s = doc.toObject(Session::class.java) ?: return@mapNotNull null
            doc.id to s
        }.filter { (_, s) ->
            s.status != "FINISHED"
        }

        // scegli la più recente (startedAt se c’è, altrimenti createdAt)
        return candidates.maxByOrNull { (_, s) -> (s.startedAt ?: s.createdAt) }?.first
    }

    // ---------------- GAME STATE (autosave) ----------------

    override suspend fun loadGameState(sessionId: String, uid: String): GameState? {
        val doc = sessions.document(sessionId)
            .collection("gameState")
            .document(uid)
            .get()
            .await()

        return if (doc.exists()) doc.toObject(GameState::class.java) else null
    }

    override suspend fun saveGameState(sessionId: String, uid: String, state: GameState) {
        sessions.document(sessionId)
            .collection("gameState")
            .document(uid)
            .set(state.copy(updatedAt = System.currentTimeMillis()))
            .await()

        // opzionale: bump updatedAt session
        sessions.document(sessionId).update("updatedAt", System.currentTimeMillis()).await()
    }

    override suspend fun finishSessionIfIdle(sessionId: String, idleTimeoutMs: Long): Boolean {
        val ref = sessions.document(sessionId)
        val now = System.currentTimeMillis()

        return db.runTransaction { tx ->
            val snap = tx.get(ref)
            if (!snap.exists()) return@runTransaction false

            val sess = snap.toObject(Session::class.java) ?: return@runTransaction false
            if (sess.status == "FINISHED") return@runTransaction false

            val updatedAt = sess.updatedAt
            val idle = now - updatedAt
            if (idle < idleTimeoutMs) return@runTransaction false

            // ✅ chiusura zombie: chiunque può farla se davvero idle (semplice e robusto)
            tx.update(
                ref,
                mapOf(
                    "status" to "FINISHED",
                    "updatedAt" to now
                )
            )
            true
        }.await()
    }

}
