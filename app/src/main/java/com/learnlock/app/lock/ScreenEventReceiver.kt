package com.learnlock.app.lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Реагирует только на фактическую разблокировку устройства.
 * Делегирует решение о показе задачи в [LockController.onScreenAwake].
 */
class ScreenEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_USER_PRESENT) {
            LockController.onScreenAwake(context)
        }
    }
}
