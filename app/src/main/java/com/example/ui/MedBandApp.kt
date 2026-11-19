package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalInformation
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.firebase.AiMapsRepository
import com.example.data.firebase.MedBandRepository
import com.example.data.firebase.PreferencesRepository
import com.example.data.firebase.attemptAutoSignIn
import com.example.data.firebase.authStateFlow
import com.example.hardware.LocationHelper
import com.example.hardware.MedBandBleManager
import com.example.localization.AppLanguage
import com.example.localization.LocalMedBandStrings
import com.example.localization.stringsFor
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BluetoothScreen
import com.example.ui.screens.EmergencyScreen
import com.example.ui.screens.HealthMonitoringScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.MedicalHistoryScreen
import com.example.ui.screens.MedicalProfileScreen
import com.example.ui.screens.MovementMonitoringScreen
import com.example.ui.screens.NfcTransferScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MedBandTheme
import com.example.ui.viewmodel.MedBandViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object MedBandRoutes {
    const val HOME = "home"
    const val HEALTH = "health"
    const val NFC = "nfc"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val BLUETOOTH = "bluetooth"
    const val HISTORY = "history"
    const val EMERGENCY = "emergency"
    const val MOVEMENT = "movement"
    const val NOTIFICATIONS = "notifications"
}

@Composable
fun MedBandApp(
    incomingNfcUri: StateFlow<String?>,
    onConsumeNfcUri: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferencesRepository = remember(context) { PreferencesRepository(context.applicationContext) }
    val prefs by preferencesRepository.preferencesFlow.collectAsStateWithLifecycle(
        initialValue = com.example.data.firebase.LocalAppPreferences()
    )
    val language = AppLanguage.fromCode(prefs.languageCode)
    val strings = stringsFor(language)

    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = CredentialManager.create(context),
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides language.layoutDirection,
        LocalMedBandStrings provides strings
    ) {
        MedBandTheme(darkTheme = prefs.darkTheme) {
            val user = currentUser
            if (user == null) {
                AuthScreen(
                    language = language,
                    onToggleLanguage = {
                        val next = if (language == AppLanguage.AR) "en" else "ar"
                        scope.launch { preferencesRepository.setLanguage(next) }
                    },
                    onAuthSuccess = {}
                )
            } else {
                key(user.uid) {
                    AuthenticatedMedBandShell(
                        currentUserId = user.uid,
                        displayName = user.displayName ?: user.email ?: "MedBand User",
                        email = user.email,
                        preferencesRepository = preferencesRepository,
                        incomingNfcUri = incomingNfcUri,
                        onConsumeNfcUri = onConsumeNfcUri
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedMedBandShell(
    currentUserId: String,
    displayName: String,
    email: String?,
    preferencesRepository: PreferencesRepository,
    incomingNfcUri: StateFlow<String?>,
    onConsumeNfcUri: () -> Unit
) {
    val strings = LocalMedBandStrings.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: MedBandRoutes.HOME
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: MedBandViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                MedBandViewModel(
                    currentUserId = currentUserId,
                    currentDisplayName = displayName,
                    currentEmail = email,
                    repository = MedBandRepository(db),
                    preferencesRepository = preferencesRepository,
                    aiMapsRepository = AiMapsRepository(),
                    bleManager = MedBandBleManager(app),
                    locationHelper = LocationHelper(app)
                )
            }
        }
    )

    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    LaunchedEffect(statusBanner) {
        val msg = statusBanner ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearStatusBanner()
    }

    val nfcUri by incomingNfcUri.collectAsStateWithLifecycle()
    LaunchedEffect(nfcUri) {
        val uri = nfcUri ?: return@LaunchedEffect
        viewModel.handleNfcUriIntent(uri)
        onConsumeNfcUri()
        navController.navigate(MedBandRoutes.NFC) { launchSingleTop = true }
    }

    // Request POST_NOTIFICATIONS on Android 13+ lazily once signed in
    var notifAsked by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (!notifAsked && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notifAsked = true
            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val topLevelItems = listOf(
        Triple(MedBandRoutes.HOME, strings.navHome, Icons.Default.Home),
        Triple(MedBandRoutes.HEALTH, strings.navHealth, Icons.Default.Favorite),
        Triple(MedBandRoutes.NFC, strings.navNfc, Icons.Default.Nfc),
        Triple(MedBandRoutes.PROFILE, strings.navProfile, Icons.Default.MedicalInformation),
        Triple(MedBandRoutes.SETTINGS, strings.navSettings, Icons.Default.Settings)
    )
    val isTopLevel = topLevelItems.any { it.first == currentRoute }

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (!isTopLevel) {
                    TopAppBar(
                        title = {
                            val title = when (currentRoute) {
                                MedBandRoutes.BLUETOOTH -> strings.navBluetooth
                                MedBandRoutes.HISTORY -> strings.navHistory
                                MedBandRoutes.EMERGENCY -> strings.navEmergency
                                MedBandRoutes.MOVEMENT -> strings.navMovement
                                MedBandRoutes.NOTIFICATIONS -> strings.navAlerts
                                else -> strings.appName
                            }
                            Text(title)
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier.testTag("top_bar_back_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            },
            bottomBar = {
                if (!useRail && isTopLevel) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        topLevelItems.forEach { (route, label, icon) ->
                            NavigationBarItem(
                                selected = currentRoute == route,
                                onClick = { navigateTopLevel(route) },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_$route")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        topLevelItems.forEach { (route, label, icon) ->
                            NavigationRailItem(
                                selected = currentRoute == route,
                                onClick = { navigateTopLevel(route) },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, maxLines = 1) },
                                modifier = Modifier.testTag("rail_$route")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = MedBandRoutes.HOME,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(MedBandRoutes.HOME) {
                        HomeDashboardScreen(
                            viewModel = viewModel,
                            userDisplayName = displayName,
                            onNavigateBluetooth = { navController.navigate(MedBandRoutes.BLUETOOTH) },
                            onNavigateNfc = { navigateTopLevel(MedBandRoutes.NFC) },
                            onNavigateNotifications = { navController.navigate(MedBandRoutes.NOTIFICATIONS) },
                            onNavigateEmergency = { navController.navigate(MedBandRoutes.EMERGENCY) },
                            onNavigateMovement = { navController.navigate(MedBandRoutes.MOVEMENT) },
                            onNavigateHistory = { navController.navigate(MedBandRoutes.HISTORY) },
                            onNavigateHealth = { navigateTopLevel(MedBandRoutes.HEALTH) }
                        )
                    }
                    composable(MedBandRoutes.HEALTH) { HealthMonitoringScreen(viewModel) }
                    composable(MedBandRoutes.NFC) { NfcTransferScreen(viewModel) }
                    composable(MedBandRoutes.PROFILE) {
                        MedicalProfileScreen(
                            viewModel = viewModel,
                            onNavigateNfc = { navigateTopLevel(MedBandRoutes.NFC) }
                        )
                    }
                    composable(MedBandRoutes.SETTINGS) {
                        SettingsScreen(
                            viewModel = viewModel,
                            onNavigateBluetooth = { navController.navigate(MedBandRoutes.BLUETOOTH) },
                            onNavigateProfile = { navigateTopLevel(MedBandRoutes.PROFILE) }
                        )
                    }
                    composable(MedBandRoutes.BLUETOOTH) { BluetoothScreen(viewModel) }
                    composable(MedBandRoutes.HISTORY) { MedicalHistoryScreen(viewModel) }
                    composable(MedBandRoutes.EMERGENCY) { EmergencyScreen(viewModel) }
                    composable(MedBandRoutes.MOVEMENT) { MovementMonitoringScreen(viewModel) }
                    composable(MedBandRoutes.NOTIFICATIONS) { NotificationsScreen(viewModel) }
                }
            }
        }
    }
}
