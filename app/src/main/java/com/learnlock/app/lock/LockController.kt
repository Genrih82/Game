package com.learnlock.app.lock

import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.os.Build
import android.provider.Settings
import com.learnlock.app.LearnLockApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Центральная точка управления блокировкой.
 * Стартует/останавливает мониторинг и поднимает [LockActivity].
 */
object LockController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lockVisible = AtomicBoolean(false)
    private val lockInFront = AtomicBoolean(false)
    private val launchNotificationShown = AtomicBoolean(false)
    private val parentHold = AtomicBoolean(false)
    private val inAllowedApp = AtomicBoolean(false)
    private const val LAUNCH_ALARM_REQUEST = 1002

    /**
     * Во время блокировки по расписанию эти пакеты не перекрываются заданием:
     * телефонная книга, звонки и SMS.
     */
    val allowedDuringLock: Set<String> = setOf(
        "com.android.contacts",
        "com.google.android.contacts",
        "com.samsung.android.contacts",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.phone",
        "com.android.mms",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging"
    )

    /** Запускает foreground-сервис, слушающий пробуждение экрана. */
    fun startMonitoring(context: Context) {
        val app = context.applicationContext
        val intent = Intent(app, LockMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(intent)
        } else {
            app.startService(intent)
        }
    }

    fun stopMonitoring(context: Context) {
        context.stopService(Intent(context, LockMonitorService::class.java))
    }

    fun scheduleNextLaunch(context: Context) {
        val appContext = context.applicationContext
        val app = appContext as? LearnLockApp ?: return
        scope.launch {
            val atMillis = app.container.repository.observeSession().first().nextLaunchAtMillis
            if (atMillis <= System.currentTimeMillis()) return@launch
            val alarmManager = appContext.getSystemService(AlarmManager::class.java)
            val pendingIntent = launchAlarmIntent(appContext)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
            }
        }
    }

    /** Снимает alarm следующего запуска. Тот же request code, что у [scheduleNextLaunch]. */
    fun cancelScheduledLaunch(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        val pendingIntent = launchAlarmIntent(appContext)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /**
     * Вызывается при SCREEN_ON / USER_PRESENT.
     * Показывает задачу только если [LearnLockRepository.shouldPresentLock] = true.
     */
    fun onScreenAwake(context: Context) {
        val appContext = context.applicationContext
        val app = appContext as? LearnLockApp ?: return
        scope.launch {
            if (app.container.repository.shouldPresentLock()) {
                showLaunchNotification(appContext)
                presentLock(appContext)
            }
        }
    }

    fun onScheduledCheck(context: Context) = onScreenAwake(context)

    /** Родитель открыл настройки или справку поверх задания. Опрос не должен их закрывать. */
    fun holdForParent() {
        parentHold.set(true)
    }

    /**
     * Сообщение Accessibility о переднем плане.
     * Своё окно не трогает удержание. Чужое, кроме книги и SMS, снимает удержание.
     */
    fun onForegroundPackage(ownApp: Boolean, allowedApp: Boolean) {
        if (ownApp) {
            inAllowedApp.set(false)
            return
        }
        if (allowedApp) {
            inAllowedApp.set(true)
            return
        }
        inAllowedApp.set(false)
        parentHold.set(false)
    }

    /**
     * Открывает экран задачи поверх приложений.
     * @param forcePreview true — показать задачу даже при активной сессии (кнопка проверки)
     * @param unlimited true — запуск с ярлыка, без лимита заданий и без разблокировки по счётчику
     */
    fun presentLock(context: Context, forcePreview: Boolean = false, unlimited: Boolean = false) {
        val practice = forcePreview || unlimited
        if (!practice && inAllowedApp.get()) return
        if (!practice && parentHold.get()) return
        if (!practice && lockVisible.get() && lockInFront.get()) return
        if (!practice) lockVisible.set(true)
        val intent = Intent(context, LockActivity::class.java).apply {
            putExtra(LockActivity.EXTRA_FORCE_PREVIEW, forcePreview)
            putExtra(LockActivity.EXTRA_UNLIMITED, unlimited || forcePreview)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            )
        }
        context.startActivity(intent)
    }

    fun showLaunchNotification(context: Context) {
        if (!launchNotificationShown.compareAndSet(false, true)) return
        val intent = Intent(context, LockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1003,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, LearnLockApp.LAUNCH_CHANNEL_ID)
            .setSmallIcon(com.learnlock.app.R.drawable.ic_launcher)
            .setContentTitle("Время задания")
            .setContentText("Решите задания, чтобы открыть телефон")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setFullScreenIntent(pendingIntent, true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(1003, notification)
        } catch (_: SecurityException) {
            launchNotificationShown.set(false)
        }
    }

    /** Убирает уведомление о задании после разблокировки телефона. */
    fun dismissLaunchNotification(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(1003)
        launchNotificationShown.set(false)
    }

    fun onLockActivityStarted() {
        lockVisible.set(true)
        lockInFront.set(true)
        parentHold.set(false)
    }

    fun onLockActivityStopped() {
        lockInFront.set(false)
    }

    fun onLockActivityClosed() {
        lockVisible.set(false)
        lockInFront.set(false)
        launchNotificationShown.set(false)
    }

    /** Есть ли разрешение «поверх других окон» (задел; MVP стартует Activity). */
    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    private fun launchAlarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            LAUNCH_ALARM_REQUEST,
            Intent(context, LaunchAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
