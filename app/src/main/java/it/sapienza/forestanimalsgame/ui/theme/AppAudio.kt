package it.sapienza.forestanimalsgame.ui.theme

import android.content.Context
import android.media.MediaPlayer
import android.media.SoundPool
import it.sapienza.forestanimalsgame.R

// OBJECT = Singleton (Esiste una sola istanza per tutta l'app)
object AppAudio {
    private var mediaPlayer: MediaPlayer? = null
    private var soundPool: SoundPool? = null
    private var clickSoundId: Int = 0
    private var winSoundId: Int = 0

    // Inizializza tutto (chiamalo nella MainActivity)
    fun init(context: Context) {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(context, R.raw.bgm_forest)
            mediaPlayer?.isLooping = true // Ripeti sempre
            mediaPlayer?.setVolume(0.4f, 0.4f) // Volume 40%
        }

        if (soundPool == null) {
            soundPool = SoundPool.Builder().setMaxStreams(5).build()
            // Assicurati di avere i file in res/raw, altrimenti metti 0 o commenta
            clickSoundId = soundPool?.load(context, R.raw.sfx_click, 1) ?: 0
            winSoundId = soundPool?.load(context, R.raw.sfx_win, 1) ?: 0
        }
    }

    fun startMusic() {
        if (mediaPlayer?.isPlaying == false) {
            mediaPlayer?.start()
        }
    }

    fun playClick() {
        if (clickSoundId != 0) soundPool?.play(clickSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun playWin() {
        if (winSoundId != 0) soundPool?.play(winSoundId, 1f, 1f, 1, 0, 1f)
    }

    // Opzionale: Ferma musica
    fun pauseMusic() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }
    }
}