package it.sapienza.forestanimalsgame.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import android.util.Log

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
                    //  Firebase ID token (quello da usare come Bearer nel backend)
                    firebaseAuth.currentUser?.getIdToken(true)
                        ?.addOnSuccessListener { result ->
                            Log.d("FIREBASE_ID_TOKEN", result.token ?: "null")
                        }
                        ?.addOnFailureListener { e ->
                            Log.e("FIREBASE_ID_TOKEN", "Failed to get token", e)
                        }
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
