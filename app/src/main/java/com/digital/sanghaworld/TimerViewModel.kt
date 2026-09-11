package com.digital.sanghaworld

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digital.sanghaworld.logging.DailyTotal
import com.digital.sanghaworld.logging.MeditationLogStore
import com.digital.sanghaworld.service.TimerService
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TimerViewModel : ViewModel() {

    private var timerService: TimerService? = null
    private val _isServiceBound = MutableStateFlow(false)
    val isServiceBound: StateFlow<Boolean> = _isServiceBound.asStateFlow()

    private val _timeLeft = MutableStateFlow(0L)
    val timeLeft: StateFlow<Long> = _timeLeft.asStateFlow()

    private val _totalDuration = MutableStateFlow(0L)
    val totalDuration: StateFlow<Long> = _totalDuration.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

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

    private val _awarenessTimeLeft = MutableStateFlow(0L)
    val awarenessTimeLeft: StateFlow<Long> = _awarenessTimeLeft.asStateFlow()

    private val _awarenessTotalDuration = MutableStateFlow(0L)
    val awarenessTotalDuration: StateFlow<Long> = _awarenessTotalDuration.asStateFlow()

    private val _awarenessInterval = MutableStateFlow(0L)
    val awarenessInterval: StateFlow<Long> = _awarenessInterval.asStateFlow()

    private val _dailyLogs = MutableStateFlow<List<DailyTotal>>(emptyList())
    val dailyLogs: StateFlow<List<DailyTotal>> = _dailyLogs.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TimerService.LocalBinder
            timerService = binder.getService()
            _isServiceBound.value = true
            
            // Observe service state
            viewModelScope.launch {
                timerService?.timeLeftInMillis?.collect { _timeLeft.value = it }
            }
            viewModelScope.launch {
                timerService?.totalDurationInMillis?.collect { _totalDuration.value = it }
            }
            viewModelScope.launch {
                timerService?.isTimerRunning?.collect { _isRunning.value = it }
            }
            viewModelScope.launch {
                timerService?.isInPrep?.collect { _isInPrep.value = it }
            }
            viewModelScope.launch {
                timerService?.hasCompleted?.collect { _hasCompleted.value = it }
            }
            viewModelScope.launch {
                timerService?.completionTitle?.collect { _completionTitle.value = it }
            }
            viewModelScope.launch {
                timerService?.completionDuration?.collect { _completionDuration.value = it }
            }
            viewModelScope.launch {
                timerService?.isAwarenessRunning?.collect { _isAwarenessRunning.value = it }
            }
            viewModelScope.launch {
                timerService?.awarenessTimeLeftInMillis?.collect { _awarenessTimeLeft.value = it }
            }
            viewModelScope.launch {
                timerService?.awarenessTotalDurationInMillis?.collect { _awarenessTotalDuration.value = it }
            }
            viewModelScope.launch {
                timerService?.awarenessIntervalInMillis?.collect { _awarenessInterval.value = it }
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            _isServiceBound.value = false
            timerService = null
        }
    }

    fun bindService(context: Context) {
        val intent = Intent(context, TimerService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService(context: Context) {
        if (_isServiceBound.value) {
            context.unbindService(connection)
            _isServiceBound.value = false
        }
    }

    fun startTimer(context: Context, durationMillis: Long, skipPrep: Boolean = false) {
        val intent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_START
            putExtra(TimerService.EXTRA_DURATION, durationMillis)
            putExtra(TimerService.EXTRA_SKIP_PREP, skipPrep)
        }
        // Ensure service is started/promoted to foreground
        context.startForegroundService(intent)
    }

    fun stopTimer(context: Context) {
        _isRunning.value = false
        timerService?.stopTimer()
        val intent = Intent(context, TimerService::class.java)
        intent.action = TimerService.ACTION_STOP
        context.startService(intent) // Send stop action
    }

    fun startAwareness(context: Context, durationMillis: Long, intervalMillis: Long) {
        val intent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_START_AWARENESS
            putExtra(TimerService.EXTRA_AWARENESS_DURATION, durationMillis)
            putExtra(TimerService.EXTRA_AWARENESS_INTERVAL, intervalMillis)
        }
        context.startForegroundService(intent)
    }

    fun stopAwareness(context: Context) {
        timerService?.stopAwareness()
        val intent = Intent(context, TimerService::class.java)
        intent.action = TimerService.ACTION_STOP_AWARENESS
        context.startService(intent)
    }

    fun clearCompletion() {
        timerService?.clearCompletion()
        _hasCompleted.value = false
        _completionTitle.value = "Session Complete"
        _completionDuration.value = 0L
    }

    fun refreshLogs(context: Context) {
        viewModelScope.launch {
            val logs = withContext(Dispatchers.IO) {
                MeditationLogStore.loadDailyTotals(context)
            }
            _dailyLogs.value = logs
        }
    }

    fun deleteLog(context: Context, date: LocalDate) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                MeditationLogStore.deleteDay(context, date)
            }
            refreshLogs(context)
        }
    }
}
