package com.learnlock.app.lock

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.learnlock.app.LearnLockApp
import com.learnlock.app.R
import com.learnlock.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground-сервис мониторинга.
 * Держит процесс живым и регистрирует [ScreenEventReceiver]
 * только на USER_PRESENT: задание не должно мешать системной блокировке.
 */
class LockMonitorService : Service() {
    private var screenReceiver: ScreenEventReceiver? = null
    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        val receiver = ScreenEventReceiver()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
        screenReceiver = receiver
        LockController.scheduleNextLaunch(this)
        LockController.onScheduledCheck(this)
        monitorScope.launch {
            while (true) {
                delay(15_000)
                updateScheduleNotification()
                LockController.onScheduledCheck(this@LockMonitorService)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Перезапуск системой после убийства процесса, если блокировка была включена.
        return START_STICKY
    }

    override fun onDestroy() {
        monitorScope.cancel()
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenReceiver = null
        super.onDestroy()
    }

    private suspend fun updateScheduleNotification() {
        val app = application as? LearnLockApp ?: return
        val session = app.container.repository.observeSession().first()
        val nextText = if (session.nextLaunchAtMillis > 0L) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(session.nextLaunchAtMillis))
        } else "не запланировано"
        getSystemService(android.app.NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification("Следующее задание: $nextText")
        )
    }

    private fun buildNotification(text: String = getString(R.string.lock_notification_text)): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, LearnLockApp.LOCK_CHANNEL_ID)
            .setContentTitle(getString(R.string.lock_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
