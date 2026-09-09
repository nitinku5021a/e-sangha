package com.digital.sanghaworld

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.digital.sanghaworld.ui.AwarenessRunningScreen
import com.digital.sanghaworld.ui.AwarenessSetupScreen
import com.digital.sanghaworld.ui.CalendarLogScreen
import com.digital.sanghaworld.ui.CompletionScreen
import com.digital.sanghaworld.ui.DrawerMenuItem
import com.digital.sanghaworld.ui.GongSettingsScreen
import com.digital.sanghaworld.ui.HomeScreen
import com.digital.sanghaworld.billing.DonationViewModel
import com.digital.sanghaworld.ui.SupportScreen
import com.digital.sanghaworld.auth.AuthViewModel
import com.digital.sanghaworld.hall.HallViewModel
import com.digital.sanghaworld.hall.ScheduleType
import com.digital.sanghaworld.ui.LoginScreen
import com.digital.sanghaworld.ui.TimerScreen
import com.digital.sanghaworld.ui.hall.CommunitySupportScreen
import com.digital.sanghaworld.ui.hall.CreateHallScreen
import com.digital.sanghaworld.ui.hall.HallDetailScreen
import com.digital.sanghaworld.ui.hall.HallLogScreen
import com.digital.sanghaworld.ui.hall.HallSittingScreen
import com.digital.sanghaworld.ui.hall.HallsHomeScreen
import com.digital.sanghaworld.ui.theme.VipassanaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: TimerViewModel by viewModels()
    private val donationViewModel: DonationViewModel by viewModels()
    private val hallViewModel: HallViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        setContent {
            VipassanaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    VipassanaApp(viewModel, donationViewModel, hallViewModel, authViewModel)
                }
            }
        }
        
        checkPermissions()
    }

    override fun onStart() {
        super.onStart()
        viewModel.bindService(this)
    }

    override fun onStop() {
        super.onStop()
        viewModel.unbindService(this)
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun VipassanaApp(
    viewModel: TimerViewModel,
    donationViewModel: DonationViewModel,
    hallViewModel: HallViewModel,
    authViewModel: AuthViewModel
) {
    val isRunning by viewModel.isRunning.collectAsState()
    val isInPrep by viewModel.isInPrep.collectAsState()
    val hasCompleted by viewModel.hasCompleted.collectAsState()
    val timeLeft by viewModel.timeLeft.collectAsState()
    val totalDuration by viewModel.totalDuration.collectAsState()
    val dailyLogs by viewModel.dailyLogs.collectAsState()
    val completionTitle by viewModel.completionTitle.collectAsState()
    val completionDuration by viewModel.completionDuration.collectAsState()
    val isAwarenessRunning by viewModel.isAwarenessRunning.collectAsState()
    val awarenessTimeLeft by viewModel.awarenessTimeLeft.collectAsState()
    val awarenessTotalDuration by viewModel.awarenessTotalDuration.collectAsState()
    val awarenessInterval by viewModel.awarenessInterval.collectAsState()
    val hallUi by hallViewModel.state.collectAsState()
    val authState by authViewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showLog by remember { mutableStateOf(false) }
    var showAwareness by remember { mutableStateOf(false) }
    var showSupport by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showHalls by remember { mutableStateOf(false) }
    var hallPage by remember { mutableStateOf("home") }

    if (!authState.ready) {
        return
    }
    if (!authState.signedIn) {
        LoginScreen(state = authState, onGoogle = { authViewModel.signInWithGoogle(context) })
        return
    }

    fun showHome() {
        showLog = false
        showAwareness = false
        showSupport = false
        showSettings = false
        showHalls = false
        hallPage = "home"
    }

    if (isRunning) {
        LaunchedEffect(hallViewModel.activeSessionId, isRunning) {
            while (isRunning && hallViewModel.activeSessionId != null) {
                hallViewModel.refreshPresence()
                delay(10_000)
            }
        }
        val stopSit = {
            val attended = (totalDuration - timeLeft).coerceAtLeast(0)
            if (hallViewModel.activeSessionId != null) {
                hallViewModel.completeSession(attended)
            }
            viewModel.stopTimer(context)
        }
        if (hallUi.sittingHallName != null) {
            HallSittingScreen(
                hallName = hallUi.sittingHallName.orEmpty(),
                timeLeft = timeLeft,
                participants = hallUi.sittingParticipants,
                expectedSeats = maxOf(
                    hallUi.sittingExpected,
                    hallUi.selectedParticipantCount,
                    hallUi.sittingCount,
                    hallUi.sittingParticipants.size
                ),
                currentUserId = hallUi.profile?.id.orEmpty(),
                onLeave = stopSit
            )
        } else {
            TimerScreen(
                timeLeft = timeLeft,
                totalDuration = totalDuration,
                isInPrep = isInPrep,
                onStop = stopSit
            )
        }
    } else if (isAwarenessRunning) {
        AwarenessRunningScreen(
            timeLeft = awarenessTimeLeft,
            totalDuration = awarenessTotalDuration,
            intervalMillis = awarenessInterval,
            onStop = { viewModel.stopAwareness(context) }
        )
    } else {
        if (hasCompleted) {
            LaunchedEffect(hasCompleted, completionDuration) {
                if (hallViewModel.activeSessionId != null) {
                    hallViewModel.completeSession(completionDuration)
                }
            }
            CompletionScreen(
                title = completionTitle,
                totalDuration = completionDuration,
                onDone = {
                    viewModel.clearCompletion()
                    showHome()
                }
            )
        } else {
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.fillMaxHeight(),
                        drawerContainerColor = MaterialTheme.colorScheme.background
                    ) {
                        Text(
                            text = "Vipassana",
                            style = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 8.dp)
                        )
                        Text(
                            text = "SILENCE  ·  INSIGHT",
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        DrawerMenuItem(
                            title = "Sit",
                            icon = Icons.Outlined.SelfImprovement,
                            selected = !showLog && !showAwareness && !showSupport && !showSettings && !showHalls,
                            onClick = {
                                showHome()
                                scope.launch { drawerState.close() }
                            }
                        )
                        DrawerMenuItem(
                            title = "Meditation halls",
                            icon = Icons.Outlined.Groups,
                            selected = showHalls,
                            onClick = {
                                showHalls = true
                                showLog = false
                                showAwareness = false
                                showSupport = false
                                showSettings = false
                                hallPage = "home"
                                hallViewModel.refresh()
                                scope.launch { drawerState.close() }
                            }
                        )
                        DrawerMenuItem(
                            title = "Meditation log",
                            icon = Icons.Outlined.CalendarMonth,
                            selected = showLog,
                            onClick = {
                                showLog = true
                                showAwareness = false
                                showSupport = false
                                showSettings = false
                                showHalls = false
                                scope.launch { drawerState.close() }
                            }
                        )
                        DrawerMenuItem(
                            title = "Be aware always",
                            icon = Icons.Outlined.NotificationsNone,
                            selected = showAwareness,
                            onClick = {
                                showAwareness = true
                                showLog = false
                                showSupport = false
                                showSettings = false
                                showHalls = false
                                scope.launch { drawerState.close() }
                            }
                        )
                        DrawerMenuItem(
                            title = "Gong sound",
                            icon = Icons.Outlined.VolumeUp,
                            selected = showSettings,
                            onClick = {
                                showSettings = true
                                showLog = false
                                showAwareness = false
                                showSupport = false
                                showHalls = false
                                scope.launch { drawerState.close() }
                            }
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        DrawerMenuItem(
                            title = "Donate",
                            icon = Icons.Outlined.FavoriteBorder,
                            selected = showSupport,
                            onClick = {
                                showSupport = true
                                showLog = false
                                showAwareness = false
                                showSettings = false
                                showHalls = false
                                scope.launch { drawerState.close() }
                            }
                        )
                        DrawerMenuItem(
                            title = "Sign out",
                            icon = Icons.Outlined.Logout,
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                authViewModel.signOut()
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    when {
                                        showLog -> "Meditation log"
                                        showAwareness -> "Be aware always"
                                        showSupport -> "Donate"
                                        showSettings -> "Gong sound"
                                        showHalls -> "Meditation halls"
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                titleContentColor = MaterialTheme.colorScheme.onBackground,
                                navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                            )
                        )
                    }
                ) { innerPadding ->
                    if (showLog) {
                        LaunchedEffect(showLog, hasCompleted, completionDuration) {
                            viewModel.refreshLogs(context)
                        }
                        CalendarLogScreen(
                            logs = dailyLogs,
                            onDelete = { date -> viewModel.deleteLog(context, date) },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (showAwareness) {
                        AwarenessSetupScreen(
                            onStart = { hours, intervalMinutes ->
                                val durationMillis = hours.toLong() * 60 * 60 * 1000
                                val intervalMillis = intervalMinutes.toLong() * 60 * 1000
                                viewModel.startAwareness(context, durationMillis, intervalMillis)
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (showSupport) {
                        SupportScreen(
                            donationViewModel = donationViewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (showSettings) {
                        GongSettingsScreen(modifier = Modifier.padding(innerPadding))
                    } else if (showHalls) {
                        when (hallPage) {
                            "create" -> CreateHallScreen(
                                onCreate = { name, desc, dur, hour, minute, type, days, vis, audio ->
                                    hallViewModel.createHall(name, desc, dur, hour, minute, type, days, vis, audio) { id ->
                                        if (id != null) hallPage = "detail"
                                    }
                                },
                                onCancel = { hallPage = "home" },
                                modifier = Modifier.padding(innerPadding)
                            )
                            "edit" -> {
                                val hall = hallUi.selectedHall
                                val schedule = hallUi.selectedSchedule
                                if (hall == null) {
                                    hallPage = "home"
                                } else {
                                    CreateHallScreen(
                                        title = "Edit hall",
                                        submitLabel = "Save",
                                        initialName = hall.name,
                                        initialDescription = hall.description,
                                        initialDurationMinutes = (hall.durationSeconds / 60).coerceAtLeast(1),
                                        initialHour = schedule?.startLocalTime?.hour ?: 6,
                                        initialMinute = schedule?.startLocalTime?.minute ?: 0,
                                        initialScheduleType = schedule?.scheduleType ?: ScheduleType.DAILY,
                                        initialVisibility = hall.visibility,
                                        initialAudioType = hall.audioType,
                                        initialTimeZone = schedule?.timezone ?: hall.timezone,
                                        onCreate = { name, desc, dur, hour, minute, type, days, vis, audio ->
                                            hallViewModel.updateHall(
                                                hall.id, name, desc, dur, hour, minute, type, days, vis, audio
                                            ) { ok ->
                                                if (ok) hallPage = "detail"
                                            }
                                        },
                                        onCancel = { hallPage = "detail" },
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                }
                            }
                            "detail" -> HallDetailScreen(
                                ui = hallUi,
                                onJoin = { hallUi.selectedHall?.id?.let { hallViewModel.joinHall(it) } },
                                onLeave = { hallUi.selectedHall?.id?.let { hallViewModel.leaveHall(it) } },
                                onArrive = {
                                    hallUi.selectedHall?.id?.let { hallViewModel.arriveForSitting(it) }
                                },
                                onEnter = {
                                    hallUi.selectedHall?.id?.let { hallId ->
                                        hallViewModel.enterSession(hallId) { remaining ->
                                            if (remaining != null) {
                                                viewModel.startTimer(context, remaining, skipPrep = true)
                                            }
                                        }
                                    }
                                },
                                onEdit = { hallPage = "edit" },
                                onDelete = {
                                    hallUi.selectedHall?.id?.let { id ->
                                        hallViewModel.deleteHall(id) { ok ->
                                            if (ok) hallPage = "home"
                                        }
                                    }
                                },
                                onBack = { hallPage = "home"; hallViewModel.refresh() },
                                modifier = Modifier.padding(innerPadding)
                            )
                            "support" -> CommunitySupportScreen(
                                ui = hallUi,
                                onToggleSupporter = { hallViewModel.setSupporterEnabled(it) },
                                onRequest = { hallViewModel.requestSupport(60, false) },
                                onCancel = { hallViewModel.cancelSupport(it) },
                                onPrivateSit = {
                                    hallViewModel.createPrivateSupportHall { id ->
                                        if (id != null) hallPage = "detail"
                                    }
                                },
                                onBack = { hallPage = "home" },
                                modifier = Modifier.padding(innerPadding)
                            )
                            "logs" -> HallLogScreen(
                                ui = hallUi,
                                onBack = { hallPage = "home" },
                                modifier = Modifier.padding(innerPadding)
                            )
                            else -> HallsHomeScreen(
                                ui = hallUi,
                                onOpenHall = {
                                    hallViewModel.openHall(it)
                                    hallPage = "detail"
                                },
                                onCreate = { hallPage = "create" },
                                onSupport = { hallPage = "support" },
                                onLogs = { hallPage = "logs" },
                                onJoinCode = { code ->
                                    hallViewModel.findByShareCode(code) { id ->
                                        if (id != null) hallPage = "detail"
                                    }
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    } else {
                        HomeScreen(
                            onDurationSelected = { duration ->
                                viewModel.startTimer(context, duration)
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
