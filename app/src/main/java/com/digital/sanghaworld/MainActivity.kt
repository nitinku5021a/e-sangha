package com.digital.sanghaworld

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.util.Consumer
import com.digital.sanghaworld.service.TimerService
import com.digital.sanghaworld.ui.AwarenessRunningScreen
import com.digital.sanghaworld.ui.AwarenessSetupScreen
import com.digital.sanghaworld.ui.CompletionScreen
import com.digital.sanghaworld.ui.DrawerMenuItem
import com.digital.sanghaworld.billing.DonationViewModel
import com.digital.sanghaworld.ui.SupportScreen
import com.digital.sanghaworld.auth.AuthViewModel
import com.digital.sanghaworld.hall.HallViewModel
import com.digital.sanghaworld.hall.SittingAlarms
import com.digital.sanghaworld.hall.ScheduleType
import com.digital.sanghaworld.logging.MeditationLogStore
import com.digital.sanghaworld.ui.LoginScreen
import com.digital.sanghaworld.ui.MySpaceScreen
import java.io.File
import com.digital.sanghaworld.ui.TimerScreen
import com.digital.sanghaworld.ui.hall.CommunitySupportScreen
import com.digital.sanghaworld.ui.hall.CommunityRoomScreen
import com.digital.sanghaworld.ui.hall.CommunitySittingScreen
import com.digital.sanghaworld.ui.hall.CreateHallScreen
import com.digital.sanghaworld.ui.hall.HallDirectoryScreen
import com.digital.sanghaworld.ui.hall.HallDetailScreen
import com.digital.sanghaworld.ui.hall.HallLogScreen
import com.digital.sanghaworld.ui.hall.HallSittingScreen
import com.digital.sanghaworld.ui.hall.HallsHomeScreen
import com.digital.sanghaworld.ui.hall.MeditationHallScreen
import com.digital.sanghaworld.audio.GongPreferences
import com.digital.sanghaworld.audio.GongPreviewPlayer
import com.digital.sanghaworld.audio.HallGuidedAudioPlayer
import com.digital.sanghaworld.hall.AudioType
import com.digital.sanghaworld.hall.camelCaseHallName
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
    val activity = context as ComponentActivity
    var showFullTimer by remember {
        mutableStateOf(activity.intent?.getBooleanExtra(TimerService.EXTRA_OPEN_TIMER, false) == true)
    }
    DisposableEffect(activity) {
        val listener = Consumer<Intent> { incoming ->
            if (incoming.getBooleanExtra(TimerService.EXTRA_OPEN_TIMER, false)) showFullTimer = true
        }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }
    var showLog by remember { mutableStateOf(false) }
    var showHalls by remember { mutableStateOf(true) }
    var showAwareness by remember { mutableStateOf(false) }
    var showSupport by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var profilePage by remember { mutableStateOf("home") }
    LaunchedEffect(Unit) {
        File(context.filesDir, "profile_photo.jpg").delete()
    }
    val practiceLogs = remember(showProfile, profilePage) {
        MeditationLogStore.loadDailyTotals(context)
    }
    var hallPage by remember { mutableStateOf("home") }
    var hallOrigin by remember { mutableStateOf("home") }
    var directorySearching by remember { mutableStateOf(false) }
    LaunchedEffect(hallPage) {
        if (hallPage != "directory") directorySearching = false
    }
    var communityMinutes by remember { mutableStateOf(30) }
    var askCommunityExit by remember { mutableStateOf(false) }
    var askQuitApp by remember { mutableStateOf(false) }
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
        if (!authState.ready) return@LaunchedEffect
        if (authState.signedIn) hallViewModel.refresh()
        else SittingAlarms.cancelAll(context)
    }
    LaunchedEffect(hallPage, hallUi.inOpenHall) {
        if (hallPage != "community") return@LaunchedEffect
        while (true) {
            if (hallUi.inOpenHall) hallViewModel.beatCommunity()
            hallViewModel.refreshCommunity()
            delay(3_000)
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
        showProfile = false
        profilePage = "home"
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
            viewModel.startTimer(context, remainingMillis, skipPrep = true, skipStartGong = fromTheStart)
        }
        if (fromTheStart) {
            openingBell.play(GongPreferences.selectedSound(context).resId) { playHallAudio() }
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

    val onNestedScreen = showProfile || showAwareness || showSupport || showSettings || (showHalls && hallPage != "home")
    val onMainScreen = !isRunning && !isAwarenessRunning && !hasCompleted && !onNestedScreen
    BackHandler(enabled = isRunning || isAwarenessRunning || hasCompleted || onNestedScreen || onMainScreen) {
        when {
            onMainScreen -> askQuitApp = true
            showProfile && profilePage != "home" -> profilePage = "home"
            showProfile -> showHome()
            isRunning && showFullTimer -> {
                showFullTimer = false
                when {
                    hallUi.inOpenHall -> hallPage = "community"
                    hallUi.meditating || !hallUi.sittingHallName.isNullOrBlank() -> hallPage = "detail"
                }
            }
            hallPage == "community" -> askCommunityExit = true
            isRunning && hallPage == "info" -> hallPage = "detail"
            isRunning && hallUi.inOpenHall && hallPage != "community" -> hallPage = "community"
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
            showHalls && hallPage == "info" && hallOrigin == "directory" -> hallPage = "directory"
            showHalls && hallPage == "info" && hallOrigin == "space" -> {
                hallOrigin = "home"
                hallPage = "home"
                profilePage = "halls"
                showProfile = true
            }
            showHalls && hallPage == "detail" && hallOrigin == "directory" -> hallPage = "info"
            showHalls && hallPage != "home" -> {
                hallPage = "home"
                hallViewModel.refresh()
            }
            else -> showHome()
        }
    }

    if (askQuitApp) {
        AlertDialog(
            onDismissRequest = { askQuitApp = false },
            title = { Text("Quit the app?") },
            confirmButton = {
                TextButton(onClick = {
                    askQuitApp = false
                    (context as Activity).finish()
                }) { Text("Quit") }
            },
            dismissButton = {
                TextButton(onClick = { askQuitApp = false }) { Text("Stay") }
            }
        )
    }

    if (askCommunityExit) {
        AlertDialog(
            onDismissRequest = { askCommunityExit = false },
            title = { Text("Leave the Open Meditation Hall?") },
            text = { Text("You can stay, leave the room, or quit the app. Your sit ends if you leave or quit.") },
            confirmButton = {
                TextButton(onClick = {
                    askCommunityExit = false
                    hallViewModel.leaveCommunity()
                    if (isRunning) endSit()
                    hallFocus = false
                    hallPage = "home"
                }) { Text("Leave room") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        askCommunityExit = false
                        hallViewModel.leaveCommunity()
                        if (isRunning) endSit()
                        (context as Activity).finish()
                    }) { Text("Quit app") }
                    TextButton(onClick = { askCommunityExit = false }) { Text("Stay") }
                }
            }
        )
    }

    if (isRunning) {
        LaunchedEffect(hallViewModel.activeSessionId, isRunning) {
            while (isRunning && hallViewModel.activeSessionId != null) {
                hallViewModel.refreshPresence()
                delay(3_000)
            }
        }
        val stopSit = { endSit() }
        if (showFullTimer) {
            TimerScreen(
                timeLeft = timeLeft,
                totalDuration = totalDuration,
                isInPrep = isInPrep,
                onStop = stopSit
            )
        } else if (hallPage == "community" || hallUi.inOpenHall) {
            CommunitySittingScreen(
                people = hallUi.communityPeople,
                currentUserId = hallUi.profile?.id,
                timeLeftMillis = timeLeft,
                onOpenTimer = { showFullTimer = true },
                onLeave = { askCommunityExit = true }
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
                people = hallUi.sittingParticipants,
                currentUserId = hallUi.profile?.id,
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
                onOpenTimer = { showFullTimer = true },
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
                        Text(
                            text = "Sangha",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 8.dp)
                        )
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
                            selected = showHalls && hallPage == "home" && !showProfile,
                            onClick = {
                                showHalls = true
                                showLog = false
                                showAwareness = false
                                showSupport = false
                                showSettings = false
                                showProfile = false
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
                                showProfile = false
                                scope.launch { drawerState.close() }
                            }
                        )
                        DrawerMenuItem(
                            title = "Support",
                            icon = Icons.Outlined.VolunteerActivism,
                            selected = showHalls && hallPage == "support",
                            onClick = {
                                showHalls = true
                                showLog = false
                                showAwareness = false
                                showSupport = false
                                showSettings = false
                                showProfile = false
                                hallPage = "support"
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
                                showProfile = false
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
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        val onHallsHome = showHalls && hallPage == "home" &&
                            !showProfile && !showSupport && !showAwareness && !showSettings
                        if (!(showHalls && hallPage == "detail") && !onHallsHome) {
                            TopAppBar(
                                title = {
                                    Text(
                                        when {
                                            showProfile && profilePage == "practice" -> "My Practice"
                                            showProfile && profilePage == "halls" -> "My Scheduled Halls"
                                            showProfile && profilePage == "settings" -> "Settings"
                                            showProfile -> "My Space"
                                            showAwareness -> "Be aware always"
                                            showSupport -> "Donate"
                                            showHalls && hallPage == "directory" -> "Scheduled halls"
                                            showHalls && hallPage == "create" -> "Create a hall"
                                            showHalls && hallPage == "edit" -> "Edit hall"
                                            showHalls && hallPage == "info" -> "Hall details"
                                            showHalls && hallPage == "support" -> "Support"
                                            showHalls && hallPage == "logs" -> "Hall log"
                                            showHalls && hallPage == "community" -> ""
                                            else -> "Meditation halls"
                                        },
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                },
                                navigationIcon = {
                                    if (showProfile) {
                                        IconButton(onClick = {
                                            if (profilePage != "home") profilePage = "home" else showHome()
                                        }) {
                                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                                        }
                                    } else if (showHalls && hallPage != "home" && hallPage != "detail") {
                                        IconButton(onClick = {
                                            when {
                                                hallPage == "info" && hallOrigin == "directory" -> hallPage = "directory"
                                                hallPage == "info" && hallOrigin == "space" -> {
                                                    hallOrigin = "home"
                                                    hallPage = "home"
                                                    profilePage = "halls"
                                                    showProfile = true
                                                }
                                                else -> {
                                                    hallPage = "home"
                                                    hallViewModel.refresh()
                                                }
                                            }
                                        }) {
                                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                                        }
                                    } else {
                                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                                        }
                                    }
                                },
                                actions = {
                                    if (showHalls && hallPage == "directory" && !showProfile && !showSupport && !showAwareness && !showSettings) {
                                        IconButton(onClick = { directorySearching = !directorySearching }) {
                                            Icon(
                                                Icons.Outlined.Search,
                                                contentDescription = "Search halls",
                                                tint = if (directorySearching) {
                                                    Color(0xFF344334)
                                                } else {
                                                    Color(0xFF526650)
                                                }
                                            )
                                        }
                                    }
                                    if (showHalls && hallPage == "home" && !showProfile && !showSupport && !showAwareness && !showSettings) {
                                        IconButton(onClick = {
                                            profilePage = "home"
                                            showProfile = true
                                        }) {
                                            Icon(Icons.Outlined.Person, contentDescription = "My Space")
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = if (showHalls && hallPage == "directory") {
                                        Color(0xFFF4F1E8)
                                    } else {
                                        MaterialTheme.colorScheme.background
                                    },
                                    titleContentColor = if (showHalls && hallPage == "directory") {
                                        Color(0xFF344334)
                                    } else {
                                        MaterialTheme.colorScheme.onBackground
                                    },
                                    navigationIconContentColor = if (showHalls && hallPage == "directory") {
                                        Color(0xFF526650)
                                    } else {
                                        MaterialTheme.colorScheme.onBackground
                                    },
                                    actionIconContentColor = if (showHalls && hallPage == "directory") {
                                        Color(0xFF526650)
                                    } else {
                                        MaterialTheme.colorScheme.onBackground
                                    }
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
                    } else if (showProfile) {
                        MySpaceScreen(
                            displayName = authState.displayName.orEmpty(),
                            logs = practiceLogs,
                            sittings = hallUi.logs,
                            halls = hallUi.discovery.myHalls,
                            page = profilePage,
                            onOpen = { profilePage = it },
                            onName = {
                                authViewModel.updateDisplayName(it)
                                hallViewModel.noteDisplayName(it)
                            },
                            onDeleteAccount = {
                                authViewModel.deleteAccount { ok ->
                                    if (ok) showHome()
                                }
                            },
                            onOpenHall = { card ->
                                hallViewModel.openHall(card.hall.id)
                                detailsFromRoom = false
                                hallOrigin = "space"
                                hallPage = "info"
                                showProfile = false
                                showHalls = true
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
                                sittingCount = hallUi.communityCount,
                                onMinutes = { communityMinutes = it },
                                onStart = {
                                    hallViewModel.enterCommunity()
                                    val length = communityMinutes * 60_000L
                                    viewModel.startTimer(context, length, skipPrep = true)
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                            "create" -> CreateHallScreen(
                                onCreate = { name, desc, dur, hour, minute, type, days, vis, audio, audioUrl, audioName, audioDur, startDate ->
                                    hallViewModel.createHall(
                                        name, desc, dur, hour, minute, type, days, vis, audio,
                                        audioUrl, audioName, audioDur, startDate
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
                                        initialName = camelCaseHallName(hall.name),
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
                                        initialDays = schedule?.daysOfWeek ?: emptySet(),
                                        initialStartDate = schedule?.startDate,
                                        onCreate = { name, desc, dur, hour, minute, type, days, vis, audio, audioUrl, audioName, audioDur, startDate ->
                                            hallViewModel.updateHall(
                                                hall.id, name, desc, dur, hour, minute, type, days, vis, audio,
                                                audioUrl, audioName, audioDur, startDate
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
                                LaunchedEffect(hallViewModel.activeSessionId) {
                                    while (hallViewModel.activeSessionId != null) {
                                        hallViewModel.refreshPresence()
                                        delay(3_000)
                                    }
                                }
                                MeditationHallScreen(
                                    hallName = hallUi.selectedHall?.name.orEmpty(),
                                    seats = hallUi.seats,
                                    people = hallUi.sittingParticipants,
                                    currentUserId = hallUi.profile?.id,
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
                                    onBack = {
                                        if (hallOrigin == "directory") hallPage = "info"
                                        else {
                                            hallPage = "home"
                                            hallViewModel.refresh()
                                        }
                                    },
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
                                    } else if (hallOrigin == "directory") {
                                        hallPage = "directory"
                                    } else if (hallOrigin == "space") {
                                        hallOrigin = "home"
                                        hallPage = "home"
                                        profilePage = "halls"
                                        showProfile = true
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
                            "directory" -> HallDirectoryScreen(
                                halls = hallUi.discovery.catalog.ifEmpty {
                                    (hallUi.discovery.sittingNow + hallUi.discovery.startingSoon + hallUi.discovery.myHalls)
                                        .distinctBy { it.hall.id }
                                },
                                searching = directorySearching,
                                onOpen = { card ->
                                    hallOrigin = "directory"
                                    hallViewModel.openHall(card.hall.id)
                                    hallPage = "info"
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                            "logs" -> HallLogScreen(
                                ui = hallUi,
                                onBack = { hallPage = "home" },
                                modifier = Modifier.padding(innerPadding)
                            )
                            else -> HallsHomeScreen(
                                ui = hallUi,
                                onOpenProfile = {
                                    profilePage = "home"
                                    showProfile = true
                                },
                                onOpenMenu = { scope.launch { drawerState.open() } },
                                onCreateHall = { hallPage = "create" },
                                onBrowseHalls = {
                                    hallViewModel.refresh()
                                    hallPage = "directory"
                                },
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
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}
