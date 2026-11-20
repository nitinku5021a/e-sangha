package com.digital.sanghaworld

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.core.content.ContextCompat
import com.digital.sanghaworld.ui.AwarenessRunningScreen
import com.digital.sanghaworld.ui.AwarenessSetupScreen
import com.digital.sanghaworld.ui.CompletionScreen
import com.digital.sanghaworld.ui.DrawerMenuItem
import com.digital.sanghaworld.ui.SanghaMark
import com.digital.sanghaworld.billing.DonationViewModel
import com.digital.sanghaworld.ui.SupportScreen
import com.digital.sanghaworld.auth.AuthViewModel
import com.digital.sanghaworld.hall.HallViewModel
import com.digital.sanghaworld.hall.SittingAlarms
import com.digital.sanghaworld.hall.ScheduleType
import com.digital.sanghaworld.ui.LoginScreen
import com.digital.sanghaworld.ui.TimerScreen
import com.digital.sanghaworld.ui.hall.CommunitySupportScreen
import com.digital.sanghaworld.ui.hall.CommunityRoomScreen
import com.digital.sanghaworld.ui.hall.CreateHallScreen
import com.digital.sanghaworld.ui.hall.HallDetailScreen
import com.digital.sanghaworld.ui.hall.HallLogScreen
import com.digital.sanghaworld.ui.hall.HallSittingScreen
import com.digital.sanghaworld.ui.hall.HallsHomeScreen
import com.digital.sanghaworld.ui.hall.MeditationHallScreen
import com.digital.sanghaworld.audio.GongCatalog
import com.digital.sanghaworld.audio.GongPreviewPlayer
import com.digital.sanghaworld.audio.HallGuidedAudioPlayer
import com.digital.sanghaworld.hall.AudioType
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
    var showHalls by remember { mutableStateOf(true) }
    var showAwareness by remember { mutableStateOf(false) }
    var showSupport by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var hallMenuOpen by remember { mutableStateOf(false) }
    var openJoinCode by remember { mutableStateOf(false) }
    var hallPage by remember { mutableStateOf("home") }
    var communityMinutes by remember { mutableStateOf(30) }
    var communityGong by remember { mutableStateOf("dhamma") }
    var detailsFromRoom by remember { mutableStateOf(false) }
    var hallMuted by remember { mutableStateOf(false) }
    var hallFocus by remember { mutableStateOf(false) }
    val openingBell = remember { GongPreviewPlayer(context.applicationContext) }
    var startedSittingKey by remember { mutableStateOf<String?>(null) }

    fun startHallAudio(remainingMillis: Long) {
        val hall = hallUi.selectedHall ?: return
        val url = hall.audioUrl
        if (hall.audioType != AudioType.FILE || url.isNullOrBlank() || remainingMillis <= 0) {
            HallGuidedAudioPlayer.stop()
            return
        }
        val elapsed = (hall.durationSeconds * 1000L - remainingMillis).coerceAtLeast(0)
        HallGuidedAudioPlayer.start(context, url, elapsed, remainingMillis, hallMuted)
    }

    LaunchedEffect(hallMuted) {
        HallGuidedAudioPlayer.setMuted(hallMuted)
    }
    LaunchedEffect(hasCompleted) {
        if (hasCompleted) {
            HallGuidedAudioPlayer.stop()
        }
    }

    LaunchedEffect(authState.signedIn) {
        if (authState.ready && !authState.signedIn) SittingAlarms.cancelAll(context)
    }
    LaunchedEffect(hallPage, hallUi.meditating) {
        if (hallPage != "community") return@LaunchedEffect
        while (true) {
            if (hallUi.meditating) hallViewModel.beatCommunity()
            hallViewModel.refreshCommunity()
            delay(10_000)
        }
    }
    LaunchedEffect(hallUi.meditating, isRunning, showHalls, hallPage) {
        SittingAlarms.inHallForStart = hallUi.meditating && (isRunning || (showHalls && hallPage == "detail"))
    }

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
        showHalls = true
        hallPage = "home"
        hallViewModel.refresh()
    }

    fun beginHallSitting(startMillis: Long, remainingMillis: Long) {
        if (remainingMillis <= 0) return
        val hall = hallUi.selectedHall
        val key = "${hall?.id}:$startMillis"
        val rejoining = startedSittingKey == key && !viewModel.isRunning.value
        if (startedSittingKey == key && viewModel.isRunning.value) return
        startedSittingKey = key
        val durationMs = (hall?.durationSeconds ?: 0) * 1000L
        val fromTheStart = !rejoining && (durationMs <= 0L || remainingMillis >= durationMs - 8_000L)
        fun playHallAudio() {
            val left = if (startMillis > 0 && durationMs > 0) {
                (startMillis + durationMs - System.currentTimeMillis()).coerceAtLeast(1_000L)
            } else remainingMillis
            startHallAudio(left)
        }
        if (!viewModel.isRunning.value) {
            viewModel.startTimer(context, remainingMillis, skipPrep = true)
        }
        if (fromTheStart) {
            val gongId = if (hall?.audioType == com.digital.sanghaworld.hall.AudioType.BELL) {
                hall.audioFileName
            } else {
                GongCatalog.DEFAULT_ID
            }
            openingBell.play(GongCatalog.byId(gongId ?: GongCatalog.DEFAULT_ID).resId) { playHallAudio() }
        } else {
            playHallAudio()
        }
    }

    fun endSit() {
        val attended = (totalDuration - timeLeft).coerceAtLeast(0)
        startedSittingKey = null
        openingBell.stop()
        HallGuidedAudioPlayer.stop()
        viewModel.stopTimer(context)
        if (hallViewModel.activeSessionId != null) {
            hallViewModel.completeSession(attended)
        }
        hallViewModel.leaveMeditationSeat()
        hallFocus = false
    }

    val onNestedScreen = showAwareness || showSupport || showSettings || (showHalls && hallPage != "home")
    BackHandler(enabled = isRunning || isAwarenessRunning || hasCompleted || onNestedScreen) {
        when {
            isRunning && hallPage == "community" -> {
                hallViewModel.leaveCommunity()
                endSit()
                hallPage = "home"
            }
            isRunning && hallPage == "info" -> hallPage = "detail"
            isRunning -> endSit()
            isAwarenessRunning -> viewModel.stopAwareness(context)
            hasCompleted -> {
                viewModel.clearCompletion()
                showHome()
            }
            showHalls && hallPage == "edit" -> hallPage = "info"
            showHalls && hallPage == "info" && detailsFromRoom -> {
                detailsFromRoom = false
                hallPage = "detail"
            }
            showHalls && hallPage != "home" -> {
                hallPage = "home"
                hallViewModel.refresh()
            }
            else -> showHome()
        }
    }

    if (isRunning) {
        LaunchedEffect(hallViewModel.activeSessionId, isRunning) {
            while (isRunning && hallViewModel.activeSessionId != null) {
                hallViewModel.refreshPresence()
                delay(10_000)
            }
        }
        val stopSit = { endSit() }
        if (hallPage == "community") {
            HallSittingScreen(
                hallName = "Community Room",
                timeLeft = timeLeft,
                seats = hallUi.seats,
                joined = true,
                muted = hallMuted,
                focusMode = hallFocus,
                onJoin = {},
                onLeave = {
                    hallViewModel.leaveCommunity()
                    hallFocus = false
                    endSit()
                    hallPage = "home"
                },
                onToggleMute = { hallMuted = !hallMuted },
                onToggleFocus = { hallFocus = !hallFocus },
                onMoreDetails = {},
                onBack = {
                    hallViewModel.leaveCommunity()
                    hallFocus = false
                    endSit()
                    hallPage = "home"
                }
            )
        } else if (hallPage == "info") {
            HallDetailScreen(
                ui = hallUi,
                onJoin = { hallUi.selectedHall?.id?.let { hallViewModel.joinHall(it) } },
                onLeave = { hallUi.selectedHall?.id?.let { hallViewModel.leaveHall(it) } },
                onEnter = { hallPage = "detail" },
                onBack = { hallPage = "detail" },
                onEdit = { hallPage = "edit" },
                onDelete = {
                    hallUi.selectedHall?.id?.let { id ->
                        hallViewModel.deleteHall(id) { ok ->
                            if (ok) {
                                endSit()
                                hallPage = "home"
                            }
                        }
                    }
                }
            )
        } else if (hallUi.sittingHallName != null || hallUi.meditating) {
            HallSittingScreen(
                hallName = hallUi.sittingHallName ?: hallUi.selectedHall?.name.orEmpty(),
                timeLeft = timeLeft,
                seats = hallUi.seats,
                joined = hallUi.meditating,
                startsAtMillis = hallUi.selectedNextStart.takeIf { it > 0 },
                muted = hallMuted,
                focusMode = hallFocus,
                onJoin = { hallViewModel.joinMeditation() },
                onLeave = {
                    hallViewModel.leaveMeditationSeat()
                    hallFocus = false
                    stopSit()
                },
                onToggleMute = { hallMuted = !hallMuted },
                onToggleFocus = { hallFocus = !hallFocus },
                onMoreDetails = { detailsFromRoom = true; hallPage = "info" },
                onBack = {
                    hallViewModel.leaveMeditationSeat()
                    hallFocus = false
                    stopSit()
                }
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
                        Row(
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SanghaMark(size = 52.dp)
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = "Sangha",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Text(
                            text = "A quiet place to sit together",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
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
                        if (!(showHalls && hallPage == "detail")) {
                            TopAppBar(
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        SanghaMark(size = 28.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                        when {
                                            showAwareness -> "Be aware always"
                                            showSupport -> "Donate"
                                            showHalls && hallPage == "create" -> "Create a hall"
                                            showHalls && hallPage == "edit" -> "Edit hall"
                                            showHalls && hallPage == "info" -> "Hall details"
                                            showHalls && hallPage == "support" -> "Support"
                                            showHalls && hallPage == "logs" -> "Hall log"
                                            showHalls && hallPage == "community" -> "Community Room"
                                            else -> "Meditation halls"
                                        },
                                        style = MaterialTheme.typography.titleLarge
                                        )
                                    }
                                },
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                                    }
                                },
                                actions = {
                                    if (showHalls && hallPage == "home") {
                                        IconButton(onClick = { hallMenuOpen = true }) {
                                            Icon(Icons.Outlined.MoreVert, contentDescription = "Hall actions")
                                        }
                                        DropdownMenu(
                                            expanded = hallMenuOpen,
                                            onDismissRequest = { hallMenuOpen = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Create a hall") },
                                                onClick = { hallMenuOpen = false; hallPage = "create" }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Join with code") },
                                                onClick = { hallMenuOpen = false; openJoinCode = true }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Support") },
                                                onClick = { hallMenuOpen = false; hallPage = "support" }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Hall log") },
                                                onClick = { hallMenuOpen = false; hallPage = "logs" }
                                            )
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background,
                                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                                )
                            )
                        }
                    }
                ) { innerPadding ->
                    if (showAwareness) {
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
                    } else {
                        when (hallPage) {
                            "community" -> CommunityRoomScreen(
                                minutes = communityMinutes,
                                gongId = communityGong,
                                onMinutes = { communityMinutes = it },
                                onGong = { id ->
                                    communityGong = id
                                    if (id != "none") openingBell.play(GongCatalog.byId(id).resId)
                                },
                                onStart = {
                                    hallViewModel.enterCommunity()
                                    val length = communityMinutes * 60_000L
                                    viewModel.startTimer(context, length, skipPrep = true)
                                    if (communityGong != "none") {
                                        openingBell.play(GongCatalog.byId(communityGong).resId)
                                    }
                                },
                                onBack = { hallPage = "home" },
                                modifier = Modifier.padding(innerPadding)
                            )
                            "create" -> CreateHallScreen(
                                onCreate = { name, desc, dur, hour, minute, type, days, vis, audio, audioUrl, audioName, audioDur ->
                                    hallViewModel.createHall(
                                        name, desc, dur, hour, minute, type, days, vis, audio,
                                        audioUrl, audioName, audioDur
                                    ) { id, showHall ->
                                        if (id != null) hallPage = if (showHall) "detail" else "info"
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
                                        initialAudioUrl = hall.audioUrl,
                                        initialAudioFileName = hall.audioFileName,
                                        initialAudioDurationSeconds = hall.audioDurationSeconds,
                                        onCreate = { name, desc, dur, hour, minute, type, days, vis, audio, audioUrl, audioName, audioDur ->
                                            hallViewModel.updateHall(
                                                hall.id, name, desc, dur, hour, minute, type, days, vis, audio,
                                                audioUrl, audioName, audioDur
                                            ) { ok ->
                                                if (ok) hallPage = "detail"
                                            }
                                        },
                                        onCancel = { hallPage = "detail" },
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                }
                            }
                            "detail" -> {
                                LaunchedEffect(hallUi.selectedHall?.id) {
                                    hallViewModel.prepareHallScene()
                                }
                                LaunchedEffect(hallUi.meditating, hallUi.selectedHall?.id, hallUi.selectedNextStart) {
                                    val hall = hallUi.selectedHall ?: return@LaunchedEffect
                                    val start = hallUi.selectedNextStart
                                    if (!hallUi.meditating || start <= 0) return@LaunchedEffect
                                    val wait = start - System.currentTimeMillis()
                                    if (wait > 0) delay(wait)
                                    if (viewModel.isRunning.value) return@LaunchedEffect
                                    val remaining = start + hall.durationSeconds * 1000L - System.currentTimeMillis()
                                    if (remaining > 0) {
                                        beginHallSitting(start, remaining)
                                    }
                                }
                                MeditationHallScreen(
                                    hallName = hallUi.selectedHall?.name.orEmpty(),
                                    seats = hallUi.seats,
                                    joined = hallUi.meditating,
                                    timeLeftMillis = if (isRunning) timeLeft else null,
                                    startsAtMillis = hallUi.selectedNextStart.takeIf { it > 0 },
                                    muted = hallMuted,
                                    focusMode = hallFocus,
                                    onPreviewAudio = hallUi.selectedHall?.audioUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                        {
                                            HallGuidedAudioPlayer.start(context, url, 0L, 20_000L, false)
                                        }
                                    },
                                    onJoin = {
                                        HallGuidedAudioPlayer.stop()
                                        val id = hallUi.selectedHall?.id ?: return@MeditationHallScreen
                                        if (!hallUi.selectedJoined) hallViewModel.joinHall(id)
                                        hallViewModel.joinMeditation()
                                        hallViewModel.enterSession(id) { remaining ->
                                            if (remaining != null && remaining > 0) {
                                                beginHallSitting(hallUi.selectedNextStart, remaining)
                                            }
                                        }
                                    },
                                    onLeaveMeditation = {
                                        hallViewModel.leaveMeditationSeat()
                                        hallFocus = false
                                        if (isRunning) {
                                            endSit()
                                        } else {
                                            startedSittingKey = null
                                        }
                                    },
                                    onToggleMute = { hallMuted = !hallMuted },
                                    onToggleFocus = { hallFocus = !hallFocus },
                                    onMoreDetails = { detailsFromRoom = true; hallPage = "info" },
                                    onBack = { hallPage = "home"; hallViewModel.refresh() },
                                    onNavHome = { showHome() },
                                    onNavHalls = { hallPage = "home" },
                                    onNavSessions = { hallPage = "logs" },
                                    onNavSangha = { hallPage = "home" },
                                    onNavProfile = {
                                        showSupport = true
                                        showHalls = false
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            "info" -> HallDetailScreen(
                                ui = hallUi,
                                onJoin = { hallUi.selectedHall?.id?.let { hallViewModel.joinHall(it) } },
                                onLeave = { hallUi.selectedHall?.id?.let { hallViewModel.leaveHall(it) } },
                                onArrive = {
                                    hallUi.selectedHall?.id?.let { hallViewModel.arriveForSitting(it) }
                                },
                                onEnter = {
                                    hallUi.selectedHall?.id?.let { hallId ->
                                        hallViewModel.joinMeditation()
                                        hallPage = "detail"
                                        hallViewModel.enterSession(hallId) { remaining ->
                                            if (remaining != null && remaining > 0) {
                                                beginHallSitting(hallUi.selectedNextStart, remaining)
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
                                onBack = {
                                    if (detailsFromRoom) {
                                        detailsFromRoom = false
                                        hallPage = "detail"
                                    } else {
                                        hallPage = "home"
                                        hallViewModel.refresh()
                                    }
                                },
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
                                onOpenCommunity = {
                                    hallPage = "community"
                                    hallViewModel.refreshCommunity()
                                },
                                onOpenHall = { card ->
                                    hallViewModel.openHall(card.hall.id)
                                    val until = card.nextStartMillis - System.currentTimeMillis()
                                    val roomOpen = card.remainingSeconds != null || until in 0..(15 * 60 * 1000L)
                                    detailsFromRoom = false
                                    hallPage = if (roomOpen) "detail" else "info"
                                },
                                openJoinCode = openJoinCode,
                                onJoinCodeConsumed = { openJoinCode = false },
                                onJoinCode = { code ->
                                    hallViewModel.findByShareCode(code) { id ->
                                        if (id != null) hallPage = "info"
                                    }
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}
