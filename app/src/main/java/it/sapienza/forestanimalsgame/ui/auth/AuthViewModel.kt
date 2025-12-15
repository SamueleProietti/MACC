package it.sapienza.forestanimalsgame.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

class AuthViewModel() : ViewModel() {

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    var currentUser: FirebaseUser? by mutableStateOf(firebaseAuth.currentUser)
        private set

    var isLoading: Boolean by mutableStateOf(false)
        private set

    var errorMessage: String? by mutableStateOf(null)
        private set

    fun signInWithGoogleIdToken(idToken: String) {
        isLoading = true
        errorMessage = null

        val credential = GoogleAuthProvider.getCredential(idToken, null)

        firebaseAuth
            .signInWithCredential(credential)
            .addOnCompleteListener { task ->
                isLoading = false
                if (task.isSuccessful) {
                    currentUser = firebaseAuth.currentUser
                } else {
                    currentUser = null
                    errorMessage =
                        task.exception?.localizedMessage ?: "Errore di autenticazione"
                }
            }
    }

    fun logout() {
        firebaseAuth.signOut()
        currentUser = null
    }
}
