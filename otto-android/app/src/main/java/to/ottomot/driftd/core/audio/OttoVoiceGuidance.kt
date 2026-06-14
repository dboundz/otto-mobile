package to.ottomot.driftd.core.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class OttoVoiceGuidance(context: Context) {
    private val appContext = context.applicationContext
    private var textToSpeech: TextToSpeech? = null
    private var ready = false
    private var pendingText: String? = null

    fun speak(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val tts = textToSpeech
        if (tts == null) {
            pendingText = trimmed
            textToSpeech =
                TextToSpeech(appContext) { status ->
                    ready = status == TextToSpeech.SUCCESS
                    if (ready) {
                        textToSpeech?.language = Locale.US
                        textToSpeech?.setSpeechRate(1.0f)
                        pendingText?.let { speakNow(it) }
                        pendingText = null
                    }
                }
            return
        }
        if (!ready) {
            pendingText = trimmed
            return
        }
        speakNow(trimmed)
    }

    fun shutdown() {
        pendingText = null
        ready = false
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    private fun speakNow(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "otto-hazard-${System.nanoTime()}")
    }
}
