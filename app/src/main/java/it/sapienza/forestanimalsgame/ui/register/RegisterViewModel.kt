package it.sapienza.forestanimalsgame.ui.register

import android.graphics.Bitmap
import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.sapienza.forestanimalsgame.data.repository.ProfileRepositoryImpl
import it.sapienza.forestanimalsgame.domain.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterViewModel(
    private val repo: ProfileRepository = ProfileRepositoryImpl()
) : ViewModel() {

    private val _location = MutableLiveData<Location?>(null)
    val location: LiveData<Location?> = _location

    private val _photo = MutableLiveData<Bitmap?>(null)          // foto appena scattata
    val photo: LiveData<Bitmap?> = _photo

    private val _photoUrl = MutableLiveData<String?>(null)       // foto salvata (da backend)
    val photoUrl: LiveData<String?> = _photoUrl

    private val _avatarId = MutableLiveData("fox")               // avatar selezionato
    val avatarId: LiveData<String> = _avatarId

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _done = MutableLiveData(false)
    val done: LiveData<Boolean> = _done

    fun setLocation(location: Location) { _location.value = location }
    fun setPhoto(bitmap: Bitmap) { _photo.value = bitmap }
    fun setAvatar(id: String) { _avatarId.value = id }

    fun loadMyProfile() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val prof = withContext(Dispatchers.IO) { repo.getMyProfile() }

                // location
                val lat = prof.lat
                val lng = prof.lng
                if (lat != null && lng != null) {
                    _location.value = Location("backend").apply {
                        latitude = lat
                        longitude = lng
                    }
                }

                // photoUrl
                _photoUrl.value = prof.photoUrl

                // avatar
                _avatarId.value = prof.avatarId ?: "fox"

                // IMPORTANT: quando rientri, la bitmap locale non c’è più
                _photo.value = null

            } catch (e: Exception) {
                // Se il profilo non esiste ancora, può tornare 404: gestiscilo senza bloccare
                _error.value = e.localizedMessage
            } finally {
                _loading.value = false
            }
        }
    }

    fun saveProfile() {
        val loc = _location.value
        val avatar = _avatarId.value ?: "fox"
        val bitmap = _photo.value

        // se non ho location né una già salvata (dopo loadMyProfile), non posso salvare
        if (loc == null) {
            _error.value = "Posizione non disponibile"
            return
        }

        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _done.value = false
            try {
                val finalPhotoUrl = withContext(Dispatchers.IO) {
                    // se ho scattato una nuova foto -> upload
                    if (bitmap != null) repo.uploadMyPhoto(bitmap)
                    else _photoUrl.value // altrimenti tengo quella già salvata
                }

                withContext(Dispatchers.IO) {
                    repo.upsertMyProfile(loc, finalPhotoUrl, avatar)
                }

                // aggiorno stato locale
                _photoUrl.value = finalPhotoUrl
                _done.value = true

            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Errore salvataggio profilo"
            } finally {
                _loading.value = false
            }
        }
    }
}
