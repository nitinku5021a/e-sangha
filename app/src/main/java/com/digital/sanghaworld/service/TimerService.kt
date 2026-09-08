package com.digital.sanghaworld.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.digital.sanghaworld.R
import com.digital.sanghaworld.audio.GongPreferences
import com.digital.sanghaworld.logging.MeditationLogStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TimerService : Service() {

    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null
    private var timer: CountDownTimer? = null
    private var prepTimer: CountDownTimer? = null
    private var awarenessTimer: CountDownTimer? = null
    private var mediaPlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var stopRunnable: Runnable? = null
    private var endGongRunnable: Runnable? = null
    private var endGongCompleteRunnable: Runnable? = null
    private var hasPlayedPreEndDong = false
    private var hasLoggedSession = false

    private val _timeLeftInMillis = MutableStateFlow(0L)
    val timeLeftInMillis: StateFlow<Long> = _timeLeftInMillis.asStateFlow()

    private val _totalDurationInMillis = MutableStateFlow(0L)
    val totalDurationInMillis: StateFlow<Long> = _totalDurationInMillis.asStateFlow()

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning.asStateFlow()

    private val _isInPrep = MutableStateFlow(false)
    val isInPrep: StateFlow<Boolean> = _isInPrep.asStateFlow()

    private val _hasCompleted = MutableStateFlow(false)
    val hasCompleted: StateFlow<Boolean> = _hasCompleted.asStateFlow()

    private val _completionTitle = MutableStateFlow("Session Complete")
    val completionTitle: StateFlow<String> = _completionTitle.asStateFlow()

    private val _completionDuration = MutableStateFlow(0L)
    val completionDuration: StateFlow<Long> = _completionDuration.asStateFlow()

    private val _isAwarenessRunning = MutableStateFlow(false)
    val isAwarenessRunning: StateFlow<Boolean> = _isAwarenessRunning.asStateFlow()

    private val _awarenessTimeLeftInMillis = MutableStateFlow(0L)
    val awarenessTimeLeftInMillis: StateFlow<Long> = _awarenessTimeLeftInMillis.asStateFlow()

    private val _awarenessTotalDurationInMillis = MutableStateFlow(0L)
    val awarenessTotalDurationInMillis: StateFlow<Long> = _awarenessTotalDurationInMillis.asStateFlow()

    private val _awarenessIntervalInMillis = MutableStateFlow(0L)
    val awarenessIntervalInMillis: StateFlow<Long> = _awarenessIntervalInMillis.asStateFlow()

    companion object {
        const val CHANNEL_ID = "VipassanaTimerChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.digital.sanghaworld.START"
        const val ACTION_STOP = "com.digital.sanghaworld.STOP"
        const val EXTRA_DURATION = "com.digital.sanghaworld.DURATION"
        const val ACTION_START_AWARENESS = "com.digital.sanghaworld.START_AWARENESS"
        const val ACTION_STOP_AWARENESS = "com.digital.sanghaworld.STOP_AWARENESS"
        const val EXTRA_AWARENESS_DURATION = "com.digital.sanghaworld.AWARENESS_DURATION"
        const val EXTRA_AWARENESS_INTERVAL = "com.digital.sanghaworld.AWARENESS_INTERVAL"
        private const val PREP_TIME_MILLIS = 8 * 1000L
        private const val PRE_END_DONG_OFFSET_MILLIS = 5 * 60 * 1000L
        private const val MIN_DURATION_FOR_PRE_DONG_MILLIS = 30 * 60 * 1000L
        private const val END_GONG_COUNT = 3
        private const val END_GONG_GAP_MILLIS = 2 * 1000L
        private const val END_GONG_WAKELOCK_MILLIS = 15 * 60 * 1000L
    }

    inner class LocalBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val duration = intent.getLongExtra(EXTRA_DURATION, 0L)
                if (duration > 0) {
                    startTimerInternal(duration)
                }
            }
            ACTION_STOP -> stopTimer()
            ACTION_START_AWARENESS -> {
                val duration = intent.getLongExtra(EXTRA_AWARENESS_DURATION, 0L)
                val interval = intent.getLongExtra(EXTRA_AWARENESS_INTERVAL, 0L)
                if (duration > 0 && interval > 0) {
                    startAwarenessInternal(duration, interval)
                }
            }
            ACTION_STOP_AWARENESS -> stopAwareness()
        }
        return START_NOT_STICKY
    }

    private fun startTimerInternal(durationMillis: Long) {
        if (_isTimerRunning.value) return
        hasPlayedPreEndDong = false
        hasLoggedSession = false
        _hasCompleted.value = false
        _completionTitle.value = "Session Complete"
        _completionDuration.value = 0L
        _isInPrep.value = true

        acquireWakeLock(
            "Vipassana::TimerWakeLock",
            durationMillis + PREP_TIME_MILLIS + END_GONG_WAKELOCK_MILLIS
        )

        // Start Foreground IMMEDIATELY
        startForeground(NOTIFICATION_ID, createNotification("Consulting the silence..."))

        _isTimerRunning.value = true
        _totalDurationInMillis.value = durationMillis
        prepTimer = object : CountDownTimer(PREP_TIME_MILLIS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _timeLeftInMillis.value = millisUntilFinished
                updateNotification("Starting in: ${formatTime(millisUntilFinished)}")
            }

            override fun onFinish() {
                _isInPrep.value = false
                // Play Start Gong at actual start
                playSelectedGong()
                timer = object : CountDownTimer(durationMillis, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        _timeLeftInMillis.value = millisUntilFinished
                        updateNotification("Time remaining: ${formatTime(millisUntilFinished)}")
                        if (!hasPlayedPreEndDong &&
                            durationMillis > MIN_DURATION_FOR_PRE_DONG_MILLIS &&
                            millisUntilFinished <= PRE_END_DONG_OFFSET_MILLIS
                        ) {
                            playSelectedGong()
                            hasPlayedPreEndDong = true
                        }
                    }

                    override fun onFinish() {
                        _timeLeftInMillis.value = 0
                        logSession(durationMillis)
                        hasLoggedSession = true
                        _completionTitle.value = "Session Complete"
                        _completionDuration.value = durationMillis
                        _isTimerRunning.value = false
                        _isInPrep.value = false
                        _hasCompleted.value = true
                        playEndGongs {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            releaseWakeLock()
                        }
                    }
                }.start()
            }
        }.start()
    }

    // Exposed for Binder if needed, but primary start is via Intent now for safety
    fun startTimerProxy(durationMillis: Long) {
       // logic moved to startTimerInternal, this module can call it or deprecated
    }

    fun clearCompletion() {
        _hasCompleted.value = false
        _completionTitle.value = "Session Complete"
        _completionDuration.value = 0L
    }

    fun stopTimer() {
        val elapsed = _totalDurationInMillis.value - _timeLeftInMillis.value
        if (!hasLoggedSession && elapsed > 0) {
            logSession(elapsed)
            hasLoggedSession = true
        }
        prepTimer?.cancel()
        timer?.cancel()
        _isTimerRunning.value = false
        _isInPrep.value = false
        _hasCompleted.value = false
        _completionTitle.value = "Session Complete"
        _completionDuration.value = 0L
        _timeLeftInMillis.value = 0
        stopForeground(STOP_FOREGROUND_REMOVE)
        releaseWakeLock()
        cancelEndGongs()
        stopRunnable?.let { mainHandler.removeCallbacks(it) }
        stopRunnable = null
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun startAwarenessInternal(durationMillis: Long, intervalMillis: Long) {
        if (_isAwarenessRunning.value || _isTimerRunning.value) return
        _hasCompleted.value = false
        _completionTitle.value = "Awareness Complete"
        _completionDuration.value = 0L
        _isAwarenessRunning.value = true
        _awarenessTotalDurationInMillis.value = durationMillis
        _awarenessIntervalInMillis.value = intervalMillis
        _awarenessTimeLeftInMillis.value = durationMillis

        acquireWakeLock(
            "Vipassana::AwarenessWakeLock",
            durationMillis + END_GONG_WAKELOCK_MILLIS
        )

        startForeground(NOTIFICATION_ID, createNotification("Awareness in progress..."))

        var nextGongAtMillis = intervalMillis
        awarenessTimer = object : CountDownTimer(durationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _awarenessTimeLeftInMillis.value = millisUntilFinished
                updateNotification("Awareness: ${formatTime(millisUntilFinished)} remaining")
                val elapsed = durationMillis - millisUntilFinished
                if (nextGongAtMillis < durationMillis && elapsed >= nextGongAtMillis) {
                    playSelectedGong()
                    nextGongAtMillis += intervalMillis
                }
            }

            override fun onFinish() {
                _awarenessTimeLeftInMillis.value = 0
                _completionTitle.value = "Awareness Complete"
                _completionDuration.value = durationMillis
                _hasCompleted.value = true
                playEndGongs {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    _isAwarenessRunning.value = false
                    releaseWakeLock()
                }
            }
        }.start()
    }

    fun stopAwareness() {
        awarenessTimer?.cancel()
        _isAwarenessRunning.value = false
        _awarenessTimeLeftInMillis.value = 0
        _awarenessTotalDurationInMillis.value = 0
        _awarenessIntervalInMillis.value = 0
        _hasCompleted.value = false
        _completionTitle.value = "Session Complete"
        _completionDuration.value = 0L
        stopForeground(STOP_FOREGROUND_REMOVE)
        releaseWakeLock()
        cancelEndGongs()
        stopRunnable?.let { mainHandler.removeCallbacks(it) }
        stopRunnable = null
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun playSelectedGong(
        maxDurationMillis: Long? = null,
        onFinished: (() -> Unit)? = null
    ) {
        playSound(GongPreferences.selectedSound(this).resId, maxDurationMillis, onFinished)
    }

    private fun playSound(
        resId: Int,
        maxDurationMillis: Long? = null,
        onFinished: (() -> Unit)? = null
    ) {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            audioManager.requestAudioFocus(
                null,
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )

            stopRunnable?.let { mainHandler.removeCallbacks(it) }
            stopRunnable = null
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(this, resId)

            if (mediaPlayer == null) {
                onFinished?.invoke()
                return
            }

            mediaPlayer?.apply {
                setWakeMode(this@TimerService, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setVolume(1.0f, 1.0f)
                var finished = false
                fun finish() {
                    if (finished) return
                    finished = true
                    stopRunnable?.let { mainHandler.removeCallbacks(it) }
                    stopRunnable = null
                    onFinished?.invoke()
                }
                setOnCompletionListener { player ->
                    player.release()
                    if (mediaPlayer === player) {
                        mediaPlayer = null
                    }
                    finish()
                }
                if (maxDurationMillis != null) {
                    val stopTask = Runnable {
                        val player = mediaPlayer
                        if (player != null) {
                            try {
                                if (player.isPlaying) {
                                    player.stop()
                                }
                            } catch (_: IllegalStateException) {
                            }
                            player.release()
                            if (mediaPlayer === player) {
                                mediaPlayer = null
                            }
                        }
                        finish()
                    }
                    stopRunnable = stopTask
                    mainHandler.postDelayed(stopTask, maxDurationMillis)
                }
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onFinished?.invoke()
        }
    }

    private fun playEndGongs(onComplete: (() -> Unit)? = null) {
        cancelEndGongs()
        acquireWakeLock("Vipassana::EndGongWakeLock", END_GONG_WAKELOCK_MILLIS)
        var remaining = END_GONG_COUNT
        fun playNext() {
            playSelectedGong {
                remaining--
                if (remaining > 0) {
                    val wait = Runnable { playNext() }
                    endGongRunnable = wait
                    mainHandler.postDelayed(wait, END_GONG_GAP_MILLIS)
                } else {
                    endGongRunnable = null
                    onComplete?.invoke()
                }
            }
        }
        playNext()
    }

    private fun cancelEndGongs() {
        endGongRunnable?.let { mainHandler.removeCallbacks(it) }
        endGongRunnable = null
        endGongCompleteRunnable?.let { mainHandler.removeCallbacks(it) }
        endGongCompleteRunnable = null
    }

    private fun logSession(durationMillis: Long) {
        if (durationMillis <= 0) return
        MeditationLogStore.addSession(applicationContext, durationMillis)
    }

    private fun acquireWakeLock(tag: String, timeoutMillis: Long) {
        releaseWakeLock()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag).apply {
            setReferenceCounted(false)
            acquire(timeoutMillis)
        }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        if (lock.isHeld) {
            lock.release()
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val name = "Vipassana Timer"
        val descriptionText = "Shows active meditation timer"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, com.digital.sanghaworld.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vipassana Meditation")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(content))
    }
    
    private fun formatTime(millis: Long): String {
        val minutes = (millis / 1000) / 60
        val seconds = (millis / 1000) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
        stopAwareness()
    }
}
