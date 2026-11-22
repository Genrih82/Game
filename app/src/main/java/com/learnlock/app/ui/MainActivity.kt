package com.learnlock.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.learnlock.app.LearnLockApp
import com.learnlock.app.ui.screens.HomeScreen
import com.learnlock.app.ui.screens.HelpScreen
import com.learnlock.app.ui.screens.OnboardingScreen
import com.learnlock.app.ui.screens.ParentPinSetupScreen
import com.learnlock.app.ui.screens.ParentPinUnlockScreen
import com.learnlock.app.ui.screens.PermissionsScreen
import com.learnlock.app.ui.screens.SettingsScreen
import com.learnlock.app.ui.screens.StatsScreen
import com.learnlock.app.ui.theme.LearnLockTheme
import com.learnlock.app.ui.viewmodel.MainViewModel
import com.learnlock.app.ui.viewmodel.MainViewModelFactory

/**
 * Главная Activity приложения: навигация по домашнему экрану,
 * онбордингу, настройкам, правам и статистике.
 * Экран задачи живёт в отдельной [com.learnlock.app.lock.LockActivity].
 */
class MainActivity : ComponentActivity() {
    private val openRoute = androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openRoute.value = intent.getStringExtra(EXTRA_OPEN)
        enableEdgeToEdge()
        val app = application as LearnLockApp
        setContent {
            LearnLockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    LearnLockNav(
                        app = app,
                        openRoute = openRoute.value,
                        onOpenRouteConsumed = { openRoute.value = null }
                    )
                }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1004)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRoute.value = intent.getStringExtra(EXTRA_OPEN)
    }

    companion object {
        const val EXTRA_OPEN = "open_route"
        const val OPEN_SETTINGS = "settings"
        const val OPEN_HELP = "help"
    }
}

/** Граф навигации Compose для родительского/настроечного UI. */
@Composable
private fun LearnLockNav(
    app: LearnLockApp,
    openRoute: String?,
    onOpenRouteConsumed: () -> Unit
) {
    val navController = rememberNavController()
    val vm: MainViewModel = viewModel(
        factory = MainViewModelFactory(app, app.container.repository)
    )

    LaunchedEffect(Unit) {
        vm.bootstrap()
    }

    LaunchedEffect(vm.uiState.bootstrapped, openRoute) {
        if (!vm.uiState.bootstrapped) return@LaunchedEffect
        when (openRoute) {
            MainActivity.OPEN_SETTINGS -> {
                vm.suppressAutoTasks()
                if (vm.uiState.hasParentPin) navController.navigate("settings_pin") else navController.navigate("settings")
                onOpenRouteConsumed()
            }
            MainActivity.OPEN_HELP -> {
                vm.suppressAutoTasks()
                navController.navigate("help")
                onOpenRouteConsumed()
            }
            else -> vm.openTasksIfReady()
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                state = vm.uiState,
                onOpenOnboarding = { navController.navigate("onboarding") },
                onOpenSettings = {
                    if (vm.uiState.hasParentPin) navController.navigate("settings_pin") else navController.navigate("settings")
                },
                onOpenStats = { navController.navigate("stats") },
                onOpenHelp = { navController.navigate("help") },
                onOpenPermissions = { navController.navigate("permissions") },
                onPreviewLock = { vm.previewLock() }
            )
        }
        composable("onboarding") {
            OnboardingScreen(
                hasPin = vm.uiState.hasParentPin,
                backgroundColor = vm.uiState.profile.backgroundColor,
                onContinueToPin = { navController.navigate("pin_setup") },
                onContinueToPermissions = { navController.navigate("permissions") },
                onDone = { navController.popBackStack() }
            )
        }
        composable("pin_setup") {
            ParentPinSetupScreen(
                backgroundColor = vm.uiState.profile.backgroundColor,
                onSave = { pin ->
                    vm.setPin(pin)
                    navController.navigate("permissions_first") {
                        popUpTo("onboarding")
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable("change_pin") {
            ParentPinSetupScreen(
                backgroundColor = vm.uiState.profile.backgroundColor,
                onSave = { pin ->
                    vm.setPin(pin)
                    navController.navigate("settings") {
                        popUpTo("change_pin") { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable("permissions") {
            PermissionsScreen(
                backgroundColor = vm.uiState.profile.backgroundColor,
                onBack = { navController.popBackStack() }
            )
        }
        composable("permissions_first") {
            PermissionsScreen(
                backgroundColor = vm.uiState.profile.backgroundColor,
                onBack = { navController.popBackStack() },
                onContinue = { navController.navigate("settings") { popUpTo("permissions_first") { inclusive = true } } }
            )
        }
        composable("settings_pin") {
            var error by remember { mutableStateOf<String?>(null) }
            ParentPinUnlockScreen(
                backgroundColor = vm.uiState.profile.backgroundColor,
                error = error,
                onVerify = { pin ->
                    vm.verifySettingsPin(pin) { valid ->
                if (valid.valid) {
                            error = null
                            navController.navigate("settings") { popUpTo("settings_pin") { inclusive = true } }
                        } else {
                    error = if (valid.lockedUntilMillis > System.currentTimeMillis()) {
                        "Слишком много ошибок. Повторите через 30 секунд."
                    } else "Неверный PIN"
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                profile = vm.uiState.profile,
                nextLaunchAtMillis = vm.uiState.session.nextLaunchAtMillis,
                onChangePin = { navController.navigate("change_pin") },
                onResetPin = {
                    vm.clearParentPin()
                    navController.navigate("onboarding") { popUpTo("settings") { inclusive = true } }
                },
                onSave = { profile ->
                    vm.saveProfile(profile)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable("stats") {
            StatsScreen(
                attempts = vm.uiState.attempts,
                backgroundColor = vm.uiState.profile.backgroundColor,
                onBack = { navController.popBackStack() }
            )
        }
        composable("help") {
            HelpScreen(
                backgroundColor = vm.uiState.profile.backgroundColor,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
