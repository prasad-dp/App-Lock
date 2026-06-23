package com.example

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.data.LockPreferences
import com.example.service.AppLockSession
import com.example.ui.pattern.PatternLockView
import com.example.ui.pattern.PatternState
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class UnlockActivity : FragmentActivity() {
    private val targetPackageState = mutableStateOf<String?>(null)
    private lateinit var prefs: LockPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialPackage = intent.getStringExtra("EXTRA_PACKAGE_NAME")
        targetPackageState.value = initialPackage
        prefs = LockPreferences(this)

        // Security check: if pattern is not configured, unlock session immediately to prevent lockers soft-locks
        if (!prefs.hasPatternSet()) {
            initialPackage?.let { AppLockSession.unlockApp(it) }
            finish()
            return
        }

        setContent {
            MyApplicationTheme(darkTheme = prefs.isDarkMode) {
                Scaffold { innerPadding ->
                    com.example.ui.pattern.LockVerifyScreen(
                        prefs = prefs,
                        packageName = targetPackageState.value ?: "",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        onSuccess = {
                            targetPackageState.value?.let { AppLockSession.unlockApp(it) }
                            Toast.makeText(this, "Application Unlocked", Toast.LENGTH_SHORT).show()
                            finish()
                        },
                        onCancel = {
                            goToHome()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val packageArg = intent.getStringExtra("EXTRA_PACKAGE_NAME")
        targetPackageState.value = packageArg

        if (packageArg != null && !prefs.hasPatternSet()) {
            AppLockSession.unlockApp(packageArg)
            finish()
            return
        }
    }

    @android.annotation.SuppressLint("MissingSuperCall", "GestureBackNavigation")
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // Prevent bypassing the lock activity via back button. Close locked app and redirect to launcher home.
        goToHome()
    }

    private fun goToHome() {
        AppLockSession.activeUnlockingPackage = null
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}


