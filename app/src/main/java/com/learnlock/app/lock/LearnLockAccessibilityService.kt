package com.learnlock.app.lock

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.learnlock.app.LearnLockApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Лёгкий помощник блокировки: если сессия истекла и на передний план
 * вышло чужое приложение — снова показать экран задачи.
 *
 * Контент окон не читается (`canRetrieveWindowContent=false` в XML).
 * Не заменяет таймер: пока пользователь остаётся в одном приложении,
 * событие смены окна не придёт.
 */
class LearnLockAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val ownApp = pkg == packageName
        val allowedApp = pkg in LockController.allowedDuringLock
        LockController.onForegroundPackage(ownApp = ownApp, allowedApp = allowedApp)
        if (ownApp || allowedApp) return
        val app = application as? LearnLockApp ?: return
        scope.launch {
            if (app.container.repository.shouldPresentLock()) {
                LockController.presentLock(this@LearnLockAccessibilityService)
            }
        }
    }

    override fun onInterrupt() = Unit
}
