package com.learnlock.app.lock

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.learnlock.app.LearnLockApp
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
    override fun onDestroy() {
        LockController.onLockActivityClosed()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // В обычном режиме Back не закрывает задачу (трение).
                    if (intent.getBooleanExtra(EXTRA_FORCE_PREVIEW, false)) {
                        finish()
                    }
                }
            }
        )

        val forcePreview = intent.getBooleanExtra(EXTRA_FORCE_PREVIEW, false)
        val unlimitedMode = intent.getBooleanExtra(EXTRA_UNLIMITED_MODE, false)
        val app = application as LearnLockApp
        setContent {
            LearnLockTheme {
                val vm: LockViewModel = viewModel(
                    factory = LockViewModelFactory(app, app.container.repository)
                )
                LaunchedEffect(forcePreview, unlimitedMode) { vm.load(forcePreview = forcePreview, unlimitedMode = unlimitedMode) }
                LockTaskScreen(
                    state = vm.uiState,
                    onAnswer = { vm.submitAnswer(it) },
                    onUseHint = { vm.useHint() },
                    onParentUnlock = { pin -> vm.parentUnlock(pin) },
                    onDismissIfUnlocked = {
                        if (vm.uiState.unlocked) finish()
                    },
                    onToggleParentPin = { vm.toggleParentPin(it) }
                )
            }
        }
    }

    companion object {
        /** Extra: показать задачу для проверки UI даже при активной сессии. */
        const val EXTRA_FORCE_PREVIEW = "force_preview"
        const val EXTRA_UNLIMITED_MODE = "unlimited_mode"
    }
}
