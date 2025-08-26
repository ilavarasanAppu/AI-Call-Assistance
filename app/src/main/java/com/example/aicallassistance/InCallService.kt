package com.example.aicallassistance

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.aicallassistance.networking.AiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class InCallService : Service(), TextToSpeech.OnInitListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var aiRepository: AiRepository
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var tts: TextToSpeech
    private var mediaPlayer: MediaPlayer? = null
    private var mediaRecorder: MediaRecorder? = null


    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "InCallServiceChannel"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "InCallService"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "InCallService created.")
        createNotificationChannel()
        initializeSpeechRecognizer()
        aiRepository = AiRepository()
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "InCallService started.")

        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        handler.post {
            speechRecognizer.startListening(recognizerIntent)
        }

        val sharedPreferences = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val musicUrl = sharedPreferences.getString("MUSIC_URL", "")
        if (!musicUrl.isNullOrEmpty()) {
            playBackgroundMusic(musicUrl)
        }

        startRecording()

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "InCallService destroyed.")
        handler.post {
            speechRecognizer.stopListening()
            speechRecognizer.destroy()
        }
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        stopAndReleaseMediaPlayer()
        stopRecording()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // We don't provide binding, so return null
    }

    private fun initializeSpeechRecognizer() {
        handler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                Log.e(TAG, "Speech recognition is not available on this device.")
                stopSelf() // Stop the service if speech recognition is not available
                return@post
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "onReadyForSpeech")
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "onBeginningOfSpeech")
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "onEndOfSpeech")
                    // After speech ends, restart listening.
                    handler.post { speechRecognizer.startListening(recognizerIntent) }
                }

                override fun onError(error: Int) {
                    Log.e(TAG, "onError: $error")
                    // Many errors are recoverable, so we try to restart listening.
                    handler.postDelayed({ speechRecognizer.startListening(recognizerIntent) }, 100)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        Log.d(TAG, "onResults: $text")
                        serviceScope.launch {
                            try {
                                val response = aiRepository.getAiResponse(text)
                                Log.d(TAG, "AI Response: ${response.text}")
                                speak(response.text)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error getting AI response", e)
                            }
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val partialText = matches[0]
                        Log.d(TAG, "onPartialResults: $partialText")
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "In-Call Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, notificationIntent, android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("AI Call Assistant")
            .setContentText("Actively assisting your call.")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Replace with a proper icon later
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "The Language specified is not supported!")
            } else {
                Log.d(TAG, "TTS Initialized Successfully")
            }
        } else {
            Log.e(TAG, "TTS Initialization Failed!")
        }
    }

    private fun speak(text: String) {
        if (::tts.isInitialized) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun playBackgroundMusic(url: String) {
        stopAndReleaseMediaPlayer() // Stop any previous playback
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .build()
            )
            try {
                setDataSource(url)
                setOnPreparedListener {
                    Log.d(TAG, "MediaPlayer prepared, starting playback.")
                    start()
                    isLooping = true
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what: $what, extra: $extra")
                    stopAndReleaseMediaPlayer()
                    true
                }
                prepareAsync()
                Log.d(TAG, "MediaPlayer preparing...")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting data source for MediaPlayer", e)
                stopAndReleaseMediaPlayer()
            }
        }
    }

    private fun stopAndReleaseMediaPlayer() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
        Log.d(TAG, "MediaPlayer stopped and released.")
    }

    private fun startRecording() {
        val recordingsDir = File(getExternalFilesDir(null), "Recordings")
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs()
        }
        val outputFile = File(recordingsDir, "rec_${System.currentTimeMillis()}.3gp")

        mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }).apply {
            try {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
                Log.d(TAG, "Recording started.")
            } catch (e: Exception) {
                Log.e(TAG, "MediaRecorder preparation failed", e)
                // Fallback to MIC if VOICE_COMMUNICATION fails
                try {
                    reset()
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                    setOutputFile(outputFile.absolutePath)
                    prepare()
                    start()
                    Log.d(TAG, "Recording started using MIC fallback.")
                } catch (e2: Exception) {
                    Log.e(TAG, "MediaRecorder fallback preparation failed", e2)
                    mediaRecorder = null // Ensure recorder is null if setup fails
                }
            }
        }
    }

    private fun stopRecording() {
        mediaRecorder?.let {
            try {
                it.stop()
                it.release()
                Log.d(TAG, "Recording stopped.")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping MediaRecorder", e)
            }
        }
        mediaRecorder = null
    }
}
