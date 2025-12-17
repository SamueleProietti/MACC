package it.sapienza.forestanimalsgame.ui.register

import android.graphics.Bitmap
import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class RegisterViewModel : ViewModel() {

    private val _location = MutableLiveData<Location>(null)
    val location: LiveData<Location> = _location

    private val _photo = MutableLiveData<Bitmap?>(null)
    val photo: LiveData<Bitmap?> = _photo

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    fun setLocation(location: Location) {
        _location.value = location
    }

    fun setPhoto(bitmap: Bitmap) { 
        _photo.value = bitmap 
    }

    fun completeRegistration() {
        val loc = _location.value
        val pic = _photo.value

        if (loc == null) { _error.value = "Posizione non disponibile"; return }
        if (pic == null) { _error.value = "Scatta una foto prima di continuare"; return }

        _error.value = null
        // TODO: qui chiamerete repository/server (Firebase/REST) con loc + pic
    }
}
