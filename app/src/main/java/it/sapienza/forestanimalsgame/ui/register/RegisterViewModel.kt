package it.sapienza.forestanimalsgame.ui.register

import android.graphics.Bitmap
import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel


class RegisterViewModel(
    private val repo: ProfileRepository = ProfileRepositoryImpl()
) : ViewModel() {

    private val _location = MutableLiveData<Location?>(null)
    val location: LiveData<Location?> = _location

    private val _photo = MutableLiveData<Bitmap?>(null)
    val photo: LiveData<Bitmap?> = _photo

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _done = MutableLiveData(false)
    val done: LiveData<Boolean> = _done

    fun setLocation(location: Location) { _location.value = location }
    fun setPhoto(bitmap: Bitmap) { _photo.value = bitmap }

    fun completeRegistration() {
        val loc = _location.value
        val pic = _photo.value

        if (loc == null) { _error.value = "Posizione non disponibile"; return }
        if (pic == null) { _error.value = "Scatta una foto prima di continuare"; return }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) { _error.value = "Utente non autenticato"; return }

        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _done.value = false
            try {
                withContext(Dispatchers.IO) {
                    repo.completeProfile(uid, loc, pic)
                }
                _done.value = true
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Errore salvataggio profilo"
            } finally {
                _loading.value = false
            }
        }
    }
}
