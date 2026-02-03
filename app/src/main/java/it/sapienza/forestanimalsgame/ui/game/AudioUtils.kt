package it.sapienza.forestanimalsgame.ui.game

import android.content.Context
import android.media.MediaPlayer
import android.media.SoundPool
import it.sapienza.forestanimalsgame.R

class AudioManager(context: Context) {
    // Gestione Effetti Sonori (SFX) - Bassa latenza
    private val soundPool = SoundPool.Builder().setMaxStreams(5).build()

    // Carica i suoni
    private val clickSoundId = soundPool.load(context, R.raw.sfx_click, 1)
    private val winSoundId = soundPool.load(context, R.raw.sfx_win, 1)

    // Gestione Musica di Sottofondo (BGM)
    private var mediaPlayer: MediaPlayer? = null
    private val contextRef = context

    fun playClick() {
        // play(soundId, leftVol, rightVol, priority, loop, rate)
        soundPool.play(clickSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun playWin() {
        soundPool.play(winSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun startMusic() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(contextRef, R.raw.bgm_forest)
            mediaPlayer?.isLooping = true // Ripeti all'infinito
            mediaPlayer?.setVolume(0.5f, 0.5f) // Volume al 50%
        }
        if (mediaPlayer?.isPlaying == false) {
            mediaPlayer?.start()
        }
    }

    fun pauseMusic() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
        soundPool.release()
    }
}