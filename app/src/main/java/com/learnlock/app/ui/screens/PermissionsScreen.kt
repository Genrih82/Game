package com.learnlock.app.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.learnlock.app.lock.LearnLockAccessibilityService
import com.learnlock.app.lock.LockController
import com.learnlock.app.ui.components.LlBackground
import com.learnlock.app.ui.components.LlGhostButton
import com.learnlock.app.ui.components.LlPanel
import com.learnlock.app.ui.components.LlPrimaryButton
import com.learnlock.app.ui.theme.LlColors

private data class PermissionStep(
    val title: String,
    val body: String,
    val actionLabel: String,
    val applicable: Boolean,
    val isGranted: () -> Boolean,
    val open: (Context) -> Unit
)

/**
 * Пошаговый мастер системных прав.
 * Статусы обновляются при возврате на экран (Lifecycle.ON_RESUME).
 */
@Composable
fun PermissionsScreen(backgroundColor: Int = 0, onBack: () -> Unit, onContinue: (() -> Unit)? = null) {
    val context = LocalContext.current
    var overlay by remember { mutableStateOf(LockController.canDrawOverlays(context)) }
    var accessibility by remember { mutableStateOf(isAccessibilityEnabled(context)) }
    var batteryIgnored by remember { mutableStateOf(isIgnoringBattery(context)) }
    var exactAlarms by remember { mutableStateOf(canScheduleExactAlarms(context)) }
    var notifications by remember { mutableStateOf(notificationsAllowed(context)) }
    var fullScreenIntent by remember { mutableStateOf(canUseFullScreenIntent(context)) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notifications = granted }

    val refresh: () -> Unit = {
        overlay = LockController.canDrawOverlays(context)
        accessibility = isAccessibilityEnabled(context)
        batteryIgnored = isIgnoringBattery(context)
        exactAlarms = canScheduleExactAlarms(context)
        notifications = notificationsAllowed(context)
        fullScreenIntent = canUseFullScreenIntent(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val steps = remember(overlay, accessibility, batteryIgnored, exactAlarms, notifications, fullScreenIntent) {
        listOf(
            PermissionStep(
                title = "Поверх других окон",
                body = "Чтобы задание появлялось поверх игр и браузера.",
                actionLabel = "Открыть настройку поверх окон",
                applicable = true,
                isGranted = { overlay },
                open = { ctx ->
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${ctx.packageName}")
                        )
                    )
                }
            ),
            PermissionStep(
                title = "Специальные возможности",
                body = "Чтобы вернуть задание, если ребёнок ушёл в другое приложение.",
                actionLabel = "Открыть специальные возможности",
                applicable = true,
                isGranted = { accessibility },
                open = { ctx -> ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            ),
            PermissionStep(
                title = "Батарея",
                body = "Чтобы LearnLock не засыпал и запускался после сна.",
                actionLabel = "Открыть настройку батареи",
                applicable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M,
                isGranted = { batteryIgnored },
                open = { ctx ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${ctx.packageName}")
                            }
                        )
                    }
                }
            ),
            PermissionStep(
                title = "Точные запуски",
                body = "Чтобы задание приходило вовремя, а не «когда система захочет».",
                actionLabel = "Разрешить точные запуски",
                applicable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                isGranted = { exactAlarms },
                open = { ctx ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${ctx.packageName}")
                            }
                        )
                    }
                }
            ),
            PermissionStep(
                title = "Уведомления",
                body = "Нужны для напоминания о задании и работы сервиса.",
                actionLabel = "Разрешить уведомления",
                applicable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                isGranted = { notifications },
                open = { /* handled via launcher below */ }
            ),
            PermissionStep(
                title = "Полноэкранный запуск",
                body = "Чтобы задание могло открыться поверх экрана блокировки.",
                actionLabel = "Разрешить полноэкранный запуск",
                applicable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                isGranted = { fullScreenIntent },
                open = { ctx ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                data = Uri.parse("package:${ctx.packageName}")
                            }
                        )
                    }
                }
            )
        ).filter { it.applicable }
    }

    var stepIndex by remember { mutableIntStateOf(0) }
    if (stepIndex >= steps.size) stepIndex = steps.lastIndex.coerceAtLeast(0)
    val step = steps.getOrNull(stepIndex)
    val allOk = steps.all { it.isGranted() }
    val isLast = stepIndex >= steps.lastIndex

    LlBackground(backgroundColor = backgroundColor) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Права Android", style = MaterialTheme.typography.displayMedium, color = LlColors.MintDeep)
            if (steps.isNotEmpty()) {
                Text(
                    "Шаг ${stepIndex + 1} из ${steps.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = LlColors.MintDeep
                )
            }
            Text(
                "Включайте по одному. После возврата из настроек статус обновится сам.",
                style = MaterialTheme.typography.bodyMedium,
                color = LlColors.TextSecondary
            )

            if (step != null) {
                LlPanel {
                    Text(step.title, style = MaterialTheme.typography.titleLarge, color = LlColors.MintDeep)
                    Text(step.body, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Статус: ${status(step.isGranted())}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (step.isGranted()) LlColors.Success else LlColors.Coral
                    )
                    LlPrimaryButton(
                        text = step.actionLabel,
                        onClick = {
                            if (step.title == "Уведомления" &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            ) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                step.open(context)
                            }
                        },
                        enabled = !step.isGranted(),
                        coral = !step.isGranted()
                    )
                    if (step.isGranted()) {
                        Text("Готово для этого шага.", color = LlColors.Success)
                    }
                }
            }

            if (isLast) {
                Text(
                    "На Xiaomi / Huawei / Samsung отдельно включите автозапуск LearnLock в настройках телефона.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LlColors.TextSecondary
                )
                Text(
                    "Если Samsung блокирует специальные возможности: Настройки → Приложения → LearnLock → ⋮ → Разрешить ограниченные настройки.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LlColors.TextSecondary
                )
            }

            if (!isLast) {
                LlPrimaryButton(
                    text = if (step?.isGranted() == true) "Далее" else "Пропустить шаг",
                    onClick = { stepIndex = (stepIndex + 1).coerceAtMost(steps.lastIndex) }
                )
            } else {
                if (onContinue != null) {
                    LlPrimaryButton(
                        text = if (allOk) "Готово" else "Продолжить без всех прав",
                        onClick = onContinue,
                        coral = true
                    )
                } else if (allOk) {
                    Text("Все нужные права включены.", color = LlColors.Success)
                }
            }
            if (stepIndex > 0) {
                LlGhostButton("Назад по шагам", onClick = { stepIndex -= 1 })
            }
            LlGhostButton("Закрыть", onClick = onBack, contentColor = LlColors.TextSecondary)
        }
    }
}

private fun status(ok: Boolean): String = if (ok) "включено" else "нужно включить"

private fun isAccessibilityEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    if (!am.isEnabled) return false
    val expected = ComponentName(context, LearnLockAccessibilityService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
}

private fun isIgnoringBattery(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
}

private fun notificationsAllowed(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
}

private fun canUseFullScreenIntent(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
    return context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
}
