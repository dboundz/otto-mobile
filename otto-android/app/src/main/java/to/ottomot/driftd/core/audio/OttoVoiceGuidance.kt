package to.ottomot.driftd.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class OttoVoiceGuidance(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioFocusChangeListener =
        AudioManager.OnAudioFocusChangeListener { change ->
            Log.d(TAG, "Audio focus changed=$change")
        }
    private var textToSpeech: TextToSpeech? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var ready = false
    private var pendingSpeech: PendingSpeech? = null

    fun speakHazard(text: String) {
        speak(text, flush = true, utterancePrefix = "otto-hazard")
    }

    fun speakNavigation(
        text: String,
        flush: Boolean = false,
    ) {
        speak(text, flush = flush, utterancePrefix = "otto-navigation")
    }

    fun speak(
        text: String,
        flush: Boolean = true,
        utterancePrefix: String = "otto-voice",
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        Log.d(TAG, "Speech requested prefix=$utterancePrefix flush=$flush text=$trimmed")
        val tts = textToSpeech
        if (tts == null) {
            pendingSpeech = PendingSpeech(trimmed, flush, utterancePrefix)
            textToSpeech =
                TextToSpeech(appContext) { status ->
                    ready = status == TextToSpeech.SUCCESS
                    Log.d(TAG, "TTS init status=$status ready=$ready")
                    if (ready) {
                        configureTextToSpeech()
                        pendingSpeech?.let { speakNow(it) }
                        pendingSpeech = null
                    } else {
                        Log.w(TAG, "TTS unavailable status=$status")
                    }
                }
            return
        }
        if (!ready) {
            pendingSpeech = PendingSpeech(trimmed, flush, utterancePrefix)
            return
        }
        speakNow(PendingSpeech(trimmed, flush, utterancePrefix))
    }

    fun shutdown() {
        pendingSpeech = null
        ready = false
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        abandonAudioFocus()
    }

    private fun configureTextToSpeech() {
        val tts = textToSpeech ?: return
        val languageResult = tts.setLanguage(Locale.US)
        Log.d(TAG, "TTS language result=$languageResult locale=${Locale.US}")
        tts.setSpeechRate(1.0f)
        tts.setAudioAttributes(NavigationAudioAttributes)
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "TTS utterance start id=$utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "TTS utterance done id=$utteranceId")
                    abandonAudioFocus()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.w(TAG, "TTS utterance error id=$utteranceId")
                    abandonAudioFocus()
                }

                override fun onError(
                    utteranceId: String?,
                    errorCode: Int,
                ) {
                    Log.w(TAG, "TTS utterance error id=$utteranceId code=$errorCode")
                    abandonAudioFocus()
                }
            },
        )
    }

    private fun speakNow(speech: PendingSpeech) {
        val focusResult = requestAudioFocus()
        Log.d(TAG, "Audio focus result=$focusResult text=${speech.text}")
        val queueMode = if (speech.flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val result = textToSpeech?.speak(
            speech.text,
            queueMode,
            null,
            "${speech.utterancePrefix}-${System.nanoTime()}",
        ) ?: TextToSpeech.ERROR
        Log.d(TAG, "TTS speak result=$result queueMode=$queueMode text=${speech.text}")
    }

    private fun requestAudioFocus(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request =
                audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(NavigationAudioAttributes)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                    .also { audioFocusRequest = it }
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            )
        }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private data class PendingSpeech(
        val text: String,
        val flush: Boolean,
        val utterancePrefix: String,
    )

    private companion object {
        const val TAG = "OttoVoiceGuidance"
        val NavigationAudioAttributes: AudioAttributes =
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
    }
}
