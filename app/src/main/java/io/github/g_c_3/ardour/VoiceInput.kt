package io.github.g_c_3.ardour

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

// Voice search through the phone's speech service (DECISIONS.md 117). Listens only after a tap, stops when the app
// leaves the screen, keeps no recording. On Android 13 and newer the on-device recogniser is used when the phone has
// one; otherwise the phone's default speech service listens, which usually streams the audio to its provider and is
// outside Ardour's control.
class VoiceInput(private val activity: Activity, private val callbacks: Callbacks) {
    interface Callbacks {
        fun onListening()
        fun onLevel(level: Float)
        fun onText(text: String, isFinal: Boolean)
        fun onStopped(message: String?)
    }

    companion object {
        const val REQUEST = 71
    }

    private var recognizer: SpeechRecognizer? = null
    private var onDevice = false
    private var retried = false
    private var tag = "en-GB"
    private val main = Handler(Looper.getMainLooper())
    var active = false
        private set

    fun start(languageTag: String) {
        tag = languageTag
        retried = false
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST)
            return
        }
        begin(true)
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) begin(true) else callbacks.onStopped("Microphone is off for Ardour. Turn it on in the phone's settings.")
    }

    // Ends the listening and still delivers what was heard.
    fun stop() {
        recognizer?.stopListening()
    }

    // Drops everything without a result (app left the screen).
    fun cancel() {
        recognizer?.cancel()
        release()
    }

    @SuppressLint("NewApi") // the on-device recogniser is only created after the API 33 check
    private fun begin(preferOnDevice: Boolean) {
        release()
        val device = preferOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)
        if (!device && !SpeechRecognizer.isRecognitionAvailable(activity)) {
            callbacks.onStopped("No speech service on this phone.")
            return
        }
        onDevice = device
        val r = if (device) SpeechRecognizer.createOnDeviceSpeechRecognizer(activity) else SpeechRecognizer.createSpeechRecognizer(activity)
        recognizer = r
        r.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        active = true
        r.startListening(intent)
    }

    // SpeechRecognizer must be destroyed when done; not from inside its own callback.
    private fun release() {
        active = false
        val r = recognizer ?: return
        recognizer = null
        main.post { r.destroy() }
    }

    private fun first(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = callbacks.onListening()

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) = callbacks.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            // 12 and 13: the language is not available to the on-device recogniser; try the default service once.
            if (onDevice && !retried && (error == 12 || error == 13)) {
                retried = true
                begin(false)
                return
            }
            release()
            callbacks.onStopped(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                        "Voice search needs the phone's speech service online."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone is off for Ardour."
                    12, 13 -> "This language isn't available for voice on this phone."
                    else -> "Voice search stopped."
                },
            )
        }

        override fun onResults(results: Bundle?) {
            val text = first(results)
            release()
            if (text.isNullOrBlank()) callbacks.onStopped("Didn't catch that.") else callbacks.onText(text, true)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = first(partialResults)
            if (!text.isNullOrBlank()) callbacks.onText(text, false)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
