package com.example

import android.app.AppOpsManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.BiometricHelper
import com.example.data.LockPreferences
import com.example.service.AppLockService
import com.example.service.AppLockSession
import androidx.compose.material.icons.automirrored.filled.Launch
import com.example.ui.pattern.PatternLockView
import com.example.ui.pattern.PatternState
import com.example.ui.pattern.PinPadView
import com.example.ui.pattern.PasswordUnlockView
import com.example.ui.pattern.LockVerifyScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

object AppIconCache {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, android.graphics.drawable.Drawable>()

    fun get(packageName: String): android.graphics.drawable.Drawable? = cache[packageName]

    fun put(packageName: String, drawable: android.graphics.drawable.Drawable) {
        cache[packageName] = drawable
    }
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val prefs = remember { LockPreferences(context) }
            var isDarkMode by remember { mutableStateOf(prefs.isDarkMode) }

            MyApplicationTheme(darkTheme = isDarkMode) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LockerMainScreen(
                        isDarkMode = isDarkMode,
                        onDarkModeChange = { value ->
                            prefs.isDarkMode = value
                            isDarkMode = value
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun LockerMainScreen(
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainActivityViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? FragmentActivity }
    val setupState by viewModel.setupState.collectAsStateWithLifecycle()
    val prefs = remember { LockPreferences(context) }
    var isAppLockerUnlocked by remember { mutableStateOf(!prefs.hasPatternSet()) }

    // If locked reset is triggered, lock app locker session again
    LaunchedEffect(setupState) {
        if (setupState is SetupState.SelectLockType || setupState is SetupState.WelcomePatternRequired) {
            isAppLockerUnlocked = true
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (prefs.hasPatternSet()) {
                        isAppLockerUnlocked = false
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (!isAppLockerUnlocked && setupState is SetupState.SetupFinished) {
        LockVerifyScreen(
            prefs = prefs,
            onSuccess = {
                isAppLockerUnlocked = true
            },
            modifier = modifier,
            title = "App Locker",
            subtitle = "Verify credential to manage settings",
            onBiometricClick = {
                activity?.let { act ->
                    BiometricHelper.triggerBiometricPrompt(act, prefs) {
                        isAppLockerUnlocked = true
                    }
                }
            }
        )
    } else {
        AnimatedContent(
            targetState = setupState,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "setup_flow"
        ) { state ->
            when (state) {
                is SetupState.WelcomePatternRequired,
                is SetupState.SelectLockType,
                is SetupState.SetFirstPattern,
                is SetupState.ConfirmPattern,
                is SetupState.SetFirstPin,
                is SetupState.ConfirmPin,
                is SetupState.SetFirstPassword,
                is SetupState.ConfirmPassword,
                is SetupState.SetupSuccess -> {
                    PatternWizardView(
                        state = state,
                        isDarkMode = isDarkMode,
                        onDarkModeChange = onDarkModeChange,
                        onPatternDrawn = { viewModel.handlePatternDrawn(it) },
                        onPinEntered = { viewModel.handlePinEntered(it) },
                        onPasswordEntered = { viewModel.handlePasswordEntered(it) },
                        onSelectLockType = { viewModel.selectLockType(it) },
                        onFinish = { 
                            isAppLockerUnlocked = true
                            viewModel.completeWizard() 
                        },
                        onStart = { viewModel.startWizard() }
                    )
                }
                is SetupState.SetupFinished -> {
                    DashboardView(
                        viewModel = viewModel,
                        isDarkMode = isDarkMode,
                        onDarkModeChange = onDarkModeChange,
                        modifier = modifier
                    )
                }
            }
        }
    }
}

// 1. SETUP PATTERN / PIN / PASSWORD WIZARD SCREEN
@Composable
fun LockTypeCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("select_lock_type_$title")
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FancyThemeToggle(
    isDarkMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = updateTransition(targetState = isDarkMode, label = "theme_transition")
    
    val rotation by transition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.6f, stiffness = 120f) },
        label = "rotation"
    ) { state ->
        if (state) 0f else 180f
    }
    
    val scale by transition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.5f, stiffness = 150f) },
        label = "scale"
    ) { _ -> 1f }

    val iconTint by transition.animateColor(
        transitionSpec = { tween(200) },
        label = "tint"
    ) { state ->
        if (state) Color(0xFFFFD54F) else Color(0xFF3F51B5)
    }

    val glowColor = if (isDarkMode) Color(0xFFFFD54F).copy(alpha = 0.15f) else Color(0xFF3F51B5).copy(alpha = 0.10f)

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onToggle)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = CircleShape
            )
            .drawBehind {
                drawCircle(
                    color = glowColor,
                    radius = size.minDimension / 2f + 4.dp.toPx()
                )
            }
            .testTag("fancy_theme_toggle"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isDarkMode) Icons.Default.WbSunny else Icons.Default.NightsStay,
            contentDescription = "Toggle Theme Mode",
            tint = iconTint,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer(
                    rotationZ = rotation,
                    scaleX = scale,
                    scaleY = scale
                )
        )
    }
}

@Composable
fun PatternWizardView(
    state: SetupState,
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onPatternDrawn: (List<Int>) -> Unit,
    onPinEntered: (String) -> Unit,
    onPasswordEntered: (String) -> Unit,
    onSelectLockType: (String) -> Unit,
    onFinish: () -> Unit,
    onStart: () -> Unit
) {
    var feedbackState by remember { mutableStateOf(PatternState.DRAWING) }
    var instructionText by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        feedbackState = PatternState.DRAWING
        when (state) {
            SetupState.WelcomePatternRequired -> {
                instructionText = "Protect your system! Choose Swipe Pattern, PIN or Password to lock access."
            }
            SetupState.SelectLockType -> {
                instructionText = "Select your preferred credential protection type"
            }
            SetupState.SetFirstPattern -> {
                instructionText = "Draw a pattern lock sequence (connect at least 4 dots)"
            }
            is SetupState.ConfirmPattern -> {
                instructionText = "Draw pattern again to confirm sequence"
            }
            SetupState.SetFirstPin -> {
                instructionText = "Create a 4-digit PIN"
            }
            is SetupState.ConfirmPin -> {
                instructionText = "Enter PIN again to confirm"
            }
            SetupState.SetFirstPassword -> {
                instructionText = "Create a security password"
            }
            is SetupState.ConfirmPassword -> {
                instructionText = "Type password again to confirm"
            }
            SetupState.SetupSuccess -> {
                feedbackState = PatternState.SUCCESS
                instructionText = "Credential Configured! Your applications are now protected."
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FancyThemeToggle(
                isDarkMode = isDarkMode,
                onToggle = { onDarkModeChange(!isDarkMode) }
            )
        }

        // Shield / Lock graphics
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (state == SetupState.SetupSuccess) Icons.Default.LockOpen else Icons.Default.Lock,
                contentDescription = "Lock",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = when (state) {
                    SetupState.WelcomePatternRequired -> "Welcome to App Locker"
                    SetupState.SelectLockType -> "Choose Lock Mode"
                    SetupState.SetFirstPattern -> "Create Unlock Pattern"
                    is SetupState.ConfirmPattern -> "Confirm Unlock Pattern"
                    SetupState.SetFirstPin -> "Create Numeric PIN"
                    is SetupState.ConfirmPin -> "Confirm Numeric PIN"
                    SetupState.SetFirstPassword -> "Create Password"
                    is SetupState.ConfirmPassword -> "Confirm Password"
                    SetupState.SetupSuccess -> "Setup Successful"
                    else -> ""
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = instructionText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // Draw Pattern / PIN Keyboard / Setup Selection Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                SetupState.WelcomePatternRequired -> {
                    // Intro Button
                    Button(
                        onClick = onStart,
                        modifier = Modifier.testTag("start_wizard_button")
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Begin Setup")
                    }
                }
                SetupState.SelectLockType -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        LockTypeCard(
                            title = "Pattern Lock",
                            description = "Draw a visual connected swipe gesture",
                            icon = Icons.Default.Gesture,
                            onClick = { onSelectLockType("pattern") }
                        )
                        LockTypeCard(
                            title = "PIN Lock",
                            description = "Secure 4-digit code using numbers",
                            icon = Icons.Default.Dialpad,
                            onClick = { onSelectLockType("pin") }
                        )
                        LockTypeCard(
                            title = "Password Lock",
                            description = "Alphanumeric characters for security",
                            icon = Icons.Default.VpnKey,
                            onClick = { onSelectLockType("password") }
                        )
                    }
                }
                SetupState.SetFirstPattern, is SetupState.ConfirmPattern -> {
                    Box(modifier = Modifier.size(320.dp), contentAlignment = Alignment.Center) {
                        PatternLockView(
                            modifier = Modifier.fillMaxSize(),
                            state = feedbackState,
                            onPatternComplete = { list ->
                                if (list.size < 4) {
                                    instructionText = "Too short! Connect at least 4 dots."
                                    feedbackState = PatternState.ERROR
                                } else {
                                    onPatternDrawn(list)
                                }
                            }
                        )
                    }
                }
                SetupState.SetFirstPin, is SetupState.ConfirmPin -> {
                    PinPadView(
                        modifier = Modifier.fillMaxWidth(),
                        pinLength = 4,
                        onPinComplete = onPinEntered,
                        resetIdentifier = state
                    )
                }
                SetupState.SetFirstPassword, is SetupState.ConfirmPassword -> {
                    PasswordUnlockView(
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = if (state is SetupState.SetFirstPassword) "Create your password" else "Confirm your password",
                        onPasswordComplete = onPasswordEntered,
                        resetIdentifier = state
                    )
                }
                SetupState.SetupSuccess -> {
                    // Finished Confirm Button
                    Button(
                        onClick = onFinish,
                        modifier = Modifier.testTag("finish_wizard_button")
                    ) {
                        Text("Secure My Device")
                    }
                }
                else -> {}
            }
        }

        if (state is SetupState.ConfirmPattern || state is SetupState.ConfirmPin || state is SetupState.ConfirmPassword) {
            TextButton(
                onClick = onStart,
                modifier = Modifier.testTag("reset_wizard")
            ) {
                Text("Start Over")
            }
        } else {
            Spacer(modifier = Modifier.height(32.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// 2. MASTER DASHBOARD VIEW
@Composable
fun DashboardView(
    viewModel: MainActivityViewModel,
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? FragmentActivity }
    val appGridState by viewModel.appGridState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isLoadingApps by viewModel.isLoadingApps.collectAsStateWithLifecycle()

    var isServiceActiveState by remember { mutableStateOf(viewModel.isServiceActive()) }
    var isBiometricEnabledState by remember { mutableStateOf(viewModel.isBiometricEnabled()) }
    var isIntruderDetectionEnabledState by remember { mutableStateOf(viewModel.isIntruderDetectionEnabled()) }
    val intruderAlerts by viewModel.intruderAlertsFlow.collectAsStateWithLifecycle()

    val cameraPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setIntruderDetectionEnabled(true)
            isIntruderDetectionEnabledState = true
            Toast.makeText(context, "Intruder Detection Enabled with Camera", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.setIntruderDetectionEnabled(false)
            isIntruderDetectionEnabledState = false
            Toast.makeText(context, "Camera permission matches. Intruder detection disabled.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        val activityIntent = (context as? android.app.Activity)?.intent
        val selection = activityIntent?.getStringExtra("SELECTION")
        if (selection == "intruder_records") {
            Toast.makeText(context, "Welcome to Security Intruder Logs", Toast.LENGTH_LONG).show()
            activityIntent.removeExtra("SELECTION")
        }
    }

    // Dynamic state of System Usage Stats Permission
    var hasUsagePermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    var appToVerifyForUnlock by remember { mutableStateOf<GridAppInfo?>(null) }
    var isVerifyingToReset by remember { mutableStateOf(false) }

    if (appToVerifyForUnlock != null) {
        val app = appToVerifyForUnlock!!
        Dialog(
            onDismissRequest = { appToVerifyForUnlock = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                LockVerifyScreen(
                    prefs = LockPreferences(context),
                    packageName = app.packageName,
                    onSuccess = {
                        viewModel.toggleAppLock(app.packageName, app.appName, false)
                        appToVerifyForUnlock = null
                        Toast.makeText(context, "${app.appName} Unprotected", Toast.LENGTH_SHORT).show()
                    },
                    onCancel = {
                        appToVerifyForUnlock = null
                    },
                    onBiometricClick = {
                        activity?.let { act ->
                            BiometricHelper.triggerBiometricPrompt(act, LockPreferences(act)) {
                                viewModel.toggleAppLock(app.packageName, app.appName, false)
                                appToVerifyForUnlock = null
                                Toast.makeText(context, "${app.appName} Unprotected", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    title = "Unprotect App",
                    subtitle = "Verify credential to unprotect this application"
                )
            }
        }
    }

    if (isVerifyingToReset) {
        Dialog(
            onDismissRequest = { isVerifyingToReset = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                LockVerifyScreen(
                    prefs = LockPreferences(context),
                    onSuccess = {
                        isVerifyingToReset = false
                        viewModel.resetSetupWizard()
                        Toast.makeText(context, "Old Lock Cleared. Resetting...", Toast.LENGTH_SHORT).show()
                    },
                    onCancel = {
                        isVerifyingToReset = false
                    },
                    onBiometricClick = {
                        activity?.let { act ->
                            BiometricHelper.triggerBiometricPrompt(act, LockPreferences(act)) {
                                isVerifyingToReset = false
                                viewModel.resetSetupWizard()
                                Toast.makeText(context, "Old Lock Cleared. Resetting...", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    title = "Confirm Reset",
                    subtitle = "Verify existing credential to clear screen lock"
                )
            }
        }
    }

    // Observe lifecycle events to verify permission when returning from System Android Settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsagePermission = hasUsageStatsPermission(context)
                hasOverlayPermission = Settings.canDrawOverlays(context)
                // Check if Service should be restarted if permission is now granted
                if (hasUsagePermission && hasOverlayPermission && isServiceActiveState) {
                    val intent = Intent(context, AppLockService::class.java)
                    try {
                        ContextCompat.startForegroundService(context, intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val lockedApps = appGridState.filter { it.isLocked }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Header Toolbar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (isServiceActiveState && hasUsagePermission && hasOverlayPermission)
                                    Color(0xFFE8F5E9)
                                else Color(0xFFFFEBEE)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isServiceActiveState && hasUsagePermission && hasOverlayPermission)
                                Icons.Default.Shield
                            else Icons.Default.ShieldMoon,
                            contentDescription = "Shield State icon",
                            tint = if (isServiceActiveState && hasUsagePermission && hasOverlayPermission)
                                Color(0xFF2E7D32)
                            else Color(0xFFC62828),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "App Locker",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isServiceActiveState && hasUsagePermission && hasOverlayPermission) "Shield is active" else "Shield is sleeping",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isServiceActiveState && hasUsagePermission && hasOverlayPermission)
                                Color(0xFF2E7D32)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FancyThemeToggle(
                        isDarkMode = isDarkMode,
                        onToggle = { onDarkModeChange(!isDarkMode) }
                    )

                    // Reset Action Button
                    IconButton(
                        onClick = {
                            isVerifyingToReset = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SettingsBackupRestore,
                            contentDescription = "Reset pattern lock"
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // STEP 1: PERMISSION SHIELD CARD
            if (!hasUsagePermission) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "System Access Required",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "App Locker requires 'Usage Access' permission to check which app is in the foreground and intercept it. Please grant it in system settings.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onErrorContainer,
                                    contentColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("grant_permission_button")
                            ) {
                                Text("Grant Usage Permission")
                            }
                        }
                    }
                }
            }

            // STEP 1.5: DISPLAY LOCK OVERLAY ACCESS CARD
            if (hasUsagePermission && !hasOverlayPermission) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Overlay Permission Required",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "App Locker requires the 'Display over other apps' system permission to draw the lock screen on top of locked applications when they launch.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        ).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.tertiaryContainer
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("grant_overlay_permission_button")
                            ) {
                                Text("Grant Overlay Permission")
                            }
                        }
                    }
                }
            }

            // STEP 2: CONTROLS SETTINGS CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Security Policies",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // ACTIVE SERVICE TOGGLE
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Background App Shield",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Auto-intercept launches of locked applications",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isServiceActiveState,
                                onCheckedChange = { active ->
                                    isServiceActiveState = active
                                    viewModel.setServiceActive(active)
                                    val intent = Intent(context, AppLockService::class.java)
                                    if (active) {
                                        if (hasUsagePermission && hasOverlayPermission) {
                                            try {
                                                ContextCompat.startForegroundService(context, intent)
                                                Toast.makeText(context, "Locker service started", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                Toast.makeText(context, "Failed to start service: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Grant usage and overlay permissions first!", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        context.stopService(intent)
                                        Toast.makeText(context, "Locker service stopped", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.testTag("service_active_switch")
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        // BIOMETRIC TOGGLE
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Biometric Face / Fingerprint Unlock",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Use your device's native Face Unlock or Fingerprint scanner to instantly dismiss secure shields",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isBiometricEnabledState,
                                onCheckedChange = { fingerprint ->
                                    isBiometricEnabledState = fingerprint
                                    viewModel.setBiometricEnabled(fingerprint)
                                    Toast.makeText(
                                        context,
                                        if (fingerprint) "Biometric unlock active" else "Biometric unlock deactivated",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.testTag("biometric_active_switch")
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Intruder Camera Detection",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Snap front-facing photo on 3 failed unlock attempts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isIntruderDetectionEnabledState,
                                onCheckedChange = { active ->
                                    if (active) {
                                        val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            isIntruderDetectionEnabledState = true
                                            viewModel.setIntruderDetectionEnabled(true)
                                            Toast.makeText(context, "Intruder Detection fully armed!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                        }
                                    } else {
                                        isIntruderDetectionEnabledState = false
                                        viewModel.setIntruderDetectionEnabled(false)
                                        Toast.makeText(context, "Intruder Detection disarmed", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.testTag("intruder_detection_active_switch")
                            )
                        }
                    }
                }
            }

            // STEP 3: PLAYGROUND SIMULATOR CARD
            if (lockedApps.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Locker Simulator Playground",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Instantly test your drawing pattern or fingerprint authenticator directly inside the streaming emulator by clicking launch simulate on listed applications.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Horizontal list of locked apps to test
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                lockedApps.forEach { app ->
                                    OutlinedButton(
                                        onClick = {
                                            // Reset active unlocking package to bypass duplicate checks in Service
                                            AppLockSession.activeUnlockingPackage = null
                                            AppLockSession.lockApp(app.packageName) // make sure locked
                                            
                                            val intent = Intent(context, UnlockActivity::class.java).apply {
                                                putExtra("EXTRA_PACKAGE_NAME", app.packageName)
                                            }
                                            context.startActivity(intent)
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Launch,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = app.appName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // STEP 3.5: INTRUDER ALERT LOGS CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Camera",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Security Intruder Logs",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            if (intruderAlerts.isNotEmpty()) {
                                TextButton(
                                    onClick = { viewModel.clearAllIntruderAlerts() }
                                ) {
                                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (!isIntruderDetectionEnabledState) {
                            Text(
                                text = "Intruder photo capture is currently disabled. Toggle it in Security Policies to capture unauthorized access photos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (intruderAlerts.isEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Shield SECURE",
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "No intrusion attempts captured yet",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                intruderAlerts.forEach { alert ->
                                    val attemptedAppName = remember(alert.attemptedPackage) {
                                        if (alert.attemptedPackage.isNullOrEmpty()) {
                                            "App Locker Settings"
                                        } else {
                                            try {
                                                val pm = context.packageManager
                                                val info = pm.getApplicationInfo(alert.attemptedPackage, 0)
                                                pm.getApplicationLabel(info).toString()
                                            } catch (e: Exception) {
                                                alert.attemptedPackage
                                            }
                                        }
                                    }

                                    val dateStr = remember(alert.timestamp) {
                                        java.text.SimpleDateFormat("MMM dd, yyyy - hh:mm a", java.util.Locale.getDefault())
                                            .format(java.util.Date(alert.timestamp))
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        if (alert.photoPath.isNotEmpty()) {
                                            coil.compose.AsyncImage(
                                                model = java.io.File(alert.photoPath),
                                                contentDescription = "Intruder snapshot",
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Photo,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Target: $attemptedAppName",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Cred type: ${alert.lockType.uppercase(java.util.Locale.US)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = dateStr,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Light
                                            )
                                        }

                                        IconButton(
                                            onClick = { viewModel.deleteIntruderAlert(alert) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete record",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // STEP 4: APPLICATIONS SELECTION SECTION
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Protect Apps",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Badge(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text(
                            text = "${lockedApps.size} Locked",
                            modifier = Modifier.padding(4.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // FILTER APPS SEARCH BOX
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Search system applications...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear research")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_app_text_field"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }

            // APPLICATIONS LIST
            if (isLoadingApps) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (appGridState.isEmpty()) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = "Search empty",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Applications Found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Try searching with a different term.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                items(appGridState, key = { it.packageName }) { appInfo ->
                    AppRowItem(
                        appInfo = appInfo,
                        onLockToggled = { locked ->
                            if (!locked) {
                                // Toggling off (Unprotecting) -> Requires credential validation!
                                appToVerifyForUnlock = appInfo
                            } else {
                                // Toggling on (Protecting) does not require validation, lock right away!
                                viewModel.toggleAppLock(appInfo.packageName, appInfo.appName, true)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AppRowItem(
    appInfo: GridAppInfo,
    onLockToggled: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var appIcon by remember(appInfo.packageName) { mutableStateOf<android.graphics.drawable.Drawable?>(null) }

    LaunchedEffect(appInfo.packageName) {
        val cached = AppIconCache.get(appInfo.packageName)
        if (cached != null) {
            appIcon = cached
            return@LaunchedEffect
        }
        val pm = context.packageManager
        val icon = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                pm.getApplicationIcon(appInfo.packageName)
            } catch (e: Exception) {
                null
            }
        }
        if (icon != null) {
            AppIconCache.put(appInfo.packageName, icon)
        }
        appIcon = icon
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_row_${appInfo.packageName}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            // Render App System Icon directly for vector adaptive compatibility in Android
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (appIcon != null) {
                    AndroidView(
                        factory = { ctx -> ImageView(ctx) },
                        update = { imageView -> imageView.setImageDrawable(appIcon) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = "Standard Android icon",
                        tint = Color.Gray,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = appInfo.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = appInfo.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = { onLockToggled(!appInfo.isLocked) },
                modifier = Modifier.testTag("lock_toggle_${appInfo.packageName}")
            ) {
                Icon(
                    imageVector = if (appInfo.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (appInfo.isLocked) "Lock applied" else "App is unlocked",
                    tint = if (appInfo.isLocked)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

// System Checker programmatic permission validator
@Suppress("DEPRECATION")
private fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}
