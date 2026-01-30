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
import retrofit2.HttpException

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

    private val _done = MutableLiveData(false)
    val done: LiveData<Boolean> = _done

    private val _loading = MutableLiveData(true) 
    val loading: LiveData<Boolean> = _loading

    fun setLocation(location: Location) { _location.value = location }
    fun setPhoto(bitmap: Bitmap) { _photo.value = bitmap }
    fun setAvatar(id: String) { _avatarId.value = id }

    fun loadMyProfile() {
        viewModelScope.launch {
            _error.value = null // Resetta errori precedenti
            // loading è già true dall'inizializzazione, ma se vuoi puoi rimetterlo a true qui
            
            try {
                val profile = repo.getMyProfile()
                
                // Se arriva qui, è un 200 OK -> Utente esistente
                // Carichiamo i dati nei campi
                _photoUrl.value = profile.photoUrl
                
                if (profile.lat != null && profile.lng != null) {
                    val l = Location("provider")
                    l.latitude = profile.lat
                    l.longitude = profile.lng
                    _location.value = l
                }
                
                if (profile.avatarId != null) {
                    _avatarId.value = profile.avatarId
                }

                // Se il profilo è completo, segnaliamo che abbiamo finito (opzionale)
                // _done.value = true 

            } catch (e: Exception) {
                // 🔍 ANALISI DELL'ERRORE
                if (e is HttpException && e.code() == 404) {
                    // ✅ CASO 404: NUOVO UTENTE
                    // Non fare nulla! Non è un errore. 
                    // L'app resterà con i campi vuoti pronti per essere compilati.
                    // (Logghiamo solo per noi sviluppatori se serve)
                    println("Nuovo utente rilevato (404), mostro form vuoto.")
                } else {
                    // ❌ ALTRI ERRORI (500, 401, 403, ecc.)
                    // Questi sono problemi veri, mostriamoli all'utente.
                    _error.value = "Errore caricamento: ${e.localizedMessage}"
                }
            } finally {
                // In ogni caso (successo, 404 o errore 500), togliamo la rotella
                _loading.value = false
            }
        }
    }

    fun saveProfile() {
        val loc = _location.value
        val avatar = _avatarId.value ?: "fox"
        val bitmap = _photo.value
        val currentUrl = _photoUrl.value

        // 1. Controllo Posizione
        if (loc == null) {
            _error.value = "Posizione non disponibile. Clicca su Aggiorna posizione."
            return
        }

        // 2. Controllo Foto (Obbligatoria!)
        // Deve esserci o una nuova foto (bitmap) o una vecchia già salvata (currentUrl)
        if (bitmap == null && currentUrl.isNullOrBlank()) {
            _error.value = "Devi scattare una foto per completare il profilo!"
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
