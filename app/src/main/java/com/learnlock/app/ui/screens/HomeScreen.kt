package com.learnlock.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.learnlock.app.ui.components.LlBackground
import com.learnlock.app.ui.components.LlGhostButton
import com.learnlock.app.ui.components.LlPanel
import com.learnlock.app.ui.components.LlPrimaryButton
import com.learnlock.app.ui.theme.LlColors
import com.learnlock.app.ui.viewmodel.MainUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Главный экран: статус блокировки/сессии и переходы в настройки,
 * права, статистику и preview задачи.
 */
@Composable
fun HomeScreen(
    state: MainUiState,
    onOpenOnboarding: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenPermissions: () -> Unit,
    onPreviewLock: () -> Unit
) {
    val firstLaunch = !state.hasParentPin
    val untilText = if (state.session.nextLaunchAtMillis > 0L) {
        SimpleDateFormat("HH:mm", Locale.getDefault())
            .format(Date(state.session.nextLaunchAtMillis))
    } else {
        "—"
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    LlBackground(backgroundColor = state.profile.backgroundColor) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { it / 8 }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.shadow(6.dp, androidx.compose.foundation.shape.CircleShape),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = LlColors.SurfaceElevated.copy(alpha = 0.94f),
                            contentColor = LlColors.MintDeep
                        )
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Настройки")
                    }
                    IconButton(
                        onClick = onOpenHelp,
                        modifier = Modifier.shadow(6.dp, androidx.compose.foundation.shape.CircleShape),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = LlColors.SurfaceElevated.copy(alpha = 0.94f),
                            contentColor = LlColors.MintDeep
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "Справка")
                    }
                }
                Text(
                    "LearnLock",
                    style = MaterialTheme.typography.displayMedium,
                    color = LlColors.MintDeep
                )
                Text(
                    "Реши — и телефон твой",
                    style = MaterialTheme.typography.headlineSmall,
                    color = LlColors.TextPrimary
                )
                Text(
                    if (state.profile.lockEnabled) {
                        "${state.profile.displayName} · ${state.profile.ageYears} лет · запускать каждые ${state.profile.launchIntervalMinutes} мин"
                    } else {
                        "${state.profile.displayName} · ${state.profile.ageYears} лет"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = LlColors.TextSecondary
                )

                if (state.profile.lockEnabled && !firstLaunch) {
                    LlPanel {
                        Text("Блокировка включена", style = MaterialTheme.typography.titleMedium)
                        Text("Запуск в: $untilText", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Заданий для разблокировки: ${state.profile.tasksToUnlock}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LlColors.TextSecondary
                        )
                    }
                }

                if (!state.hasParentPin) {
                    LlPrimaryButton("Начать настройку", onClick = onOpenOnboarding, coral = true)
                }

                LlGhostButton("Задания по желанию", onClick = onPreviewLock)
                LlGhostButton("Статистика за 7 дней", onClick = onOpenStats)

                Spacer(Modifier.height(8.dp))
                Text(
                    "Родительский PIN всегда может снять блокировку.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LlColors.TextSecondary
                )
            }
        }
    }
}
