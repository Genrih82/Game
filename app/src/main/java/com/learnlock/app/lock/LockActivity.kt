package com.learnlock.app.lock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.learnlock.app.LearnLockApp
import com.learnlock.app.ui.MainActivity
import com.learnlock.app.ui.screens.LockTaskScreen
import com.learnlock.app.ui.theme.LearnLockTheme
import com.learnlock.app.ui.viewmodel.LockViewModel
import com.learnlock.app.ui.viewmodel.LockViewModelFactory

/**
 * Полноэкранная Activity с учебной задачей.
 * Показывается поверх lock-screen (флаги SHOW_WHEN_LOCKED и др.).
 * Кнопка Back блокируется, кроме режима preview с главного экрана.
 */
class LockActivity : ComponentActivity() {
    private var modeGeneration by mutableIntStateOf(0)
    private var unlimitedMode by mutableStateOf(false)

    override fun onStart() {
        super.onStart()
        LockController.onLockActivityStarted()
    }

    override fun onStop() {
        LockController.onLockActivityStopped()
        super.onStop()
    }

    override fun onDestroy() {
        LockController.onLockActivityClosed()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView)
            .show(WindowInsetsCompat.Type.statusBars())
        // LockActivity не использует showWhenLocked/dismissKeyguard:
        // системный экран блокировки телефона всегда имеет приоритет.

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val practice = intent.getBooleanExtra(EXTRA_UNLIMITED, false) ||
                        intent.getBooleanExtra(EXTRA_FORCE_PREVIEW, false)
                    if (practice) finish()
                }
            }
        )

        unlimitedMode = intent.getBooleanExtra(EXTRA_UNLIMITED, false) ||
            intent.getBooleanExtra(EXTRA_FORCE_PREVIEW, false)
        val app = application as LearnLockApp
        setContent {
            LearnLockTheme {
                val vm: LockViewModel = viewModel(
                    factory = LockViewModelFactory(app, app.container.repository)
                )
                val generation = modeGeneration
                val unlimited = unlimitedMode
                LaunchedEffect(generation, unlimited) { vm.load(unlimited = unlimited) }
                LockTaskScreen(
                    state = vm.uiState,
                    onAnswer = { vm.submitAnswer(it) },
                    onUseHint = { vm.useHint() },
                    onParentUnlock = { pin -> vm.parentUnlock(pin) },
                    onDismissIfUnlocked = {
                        if (vm.uiState.unlocked) {
                            LockController.dismissLaunchNotification(this@LockActivity)
                            finish()
                        }
                    },
                    onToggleParentPin = { vm.toggleParentPin(it) },
                    onOpenSettings = { openAppScreen(MainActivity.OPEN_SETTINGS) },
                    onOpenHelp = { openAppScreen(MainActivity.OPEN_HELP) },
                    onOpenStats = { openAppScreen(MainActivity.OPEN_STATS) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        unlimitedMode = intent.getBooleanExtra(EXTRA_UNLIMITED, false) ||
            intent.getBooleanExtra(EXTRA_FORCE_PREVIEW, false)
        modeGeneration += 1
    }

    private fun openAppScreen(route: String) {
        LockController.holdForParent()
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN, route)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        )
    }

    companion object {
        /** Extra: показать задачу для проверки UI даже при активной сессии. */
        const val EXTRA_FORCE_PREVIEW = "force_preview"

        /** Extra: запуск с ярлыка, без лимита заданий. */
        const val EXTRA_UNLIMITED = "unlimited"
    }
}
