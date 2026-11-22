package com.learnlock.app.lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.learnlock.app.LearnLockApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LaunchAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val app = appContext as? LearnLockApp ?: return@launch
                val profile = app.container.repository.getProfileOrDefault()
                if (!profile.lockEnabled) return@launch
                LockController.startMonitoring(appContext)
                if (app.container.repository.shouldPresentLock()) {
                    LockController.showLaunchNotification(appContext)
                    LockController.presentLock(appContext)
                }
                LockController.scheduleNextLaunch(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
