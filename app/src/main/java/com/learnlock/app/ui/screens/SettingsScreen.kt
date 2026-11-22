package com.learnlock.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.learnlock.app.data.ChildProfileEntity
import com.learnlock.app.data.OutsideWindowMode
import com.learnlock.app.ui.components.LlBackground
import com.learnlock.app.ui.components.LlGhostButton
import com.learnlock.app.ui.components.LlPanel
import com.learnlock.app.ui.components.LlPrimaryButton
import com.learnlock.app.ui.theme.LlColors
import kotlin.math.roundToInt

/**
 * Редактирование профиля ребёнка: возраст, сложность, длительность сессии,
 * окно часов, категории и подсказки.
 * Чипы на узком экране переносятся через [FlowRow].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    profile: ChildProfileEntity,
    nextLaunchAtMillis: Long = 0L,
    onSave: (ChildProfileEntity) -> Unit,
    onChangePin: () -> Unit = {},
    onResetPin: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
    onBack: () -> Unit
) {
    var draft by remember(profile) {
        mutableStateOf(profile.copy(displayName = profile.displayName.takeUnless { it == "Ребёнок" } ?: ""))
    }
    val nameValid = draft.displayName.trim().length >= 2
    var nameFocused by remember { mutableStateOf(false) }
    var nameErrorShown by remember { mutableStateOf(false) }
    var resetDialogVisible by remember { mutableStateOf(false) }
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = LlColors.MintDeep,
        selectedLabelColor = LlColors.TextOnAccent,
        labelColor = Color.Black
    )
    val backgroundChipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = LlColors.MintDeep,
        selectedLabelColor = LlColors.TextOnAccent,
        labelColor = Color.Black
    )

    val backgroundChoices = listOf(
        "Мятный" to Color(0xFFEAF2F6),
        "Персиковый" to Color(0xFFFFE4D1),
        "Голубой" to Color(0xFFDCEBFF),
        "Лавандовый" to Color(0xFFEBD8FF),
        "Зелёный" to Color(0xFFD4F2DF)
    )

    key(draft.backgroundColor) {
        LlBackground(backgroundColor = draft.backgroundColor) {
            Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Настройки", style = MaterialTheme.typography.displayMedium, color = LlColors.MintDeep)

            LlPanel {
                OutlinedTextField(
                    value = draft.displayName,
                    onValueChange = { value ->
                        val capitalized = value.replaceFirstChar { first ->
                            if (first.isLowerCase()) first.titlecase() else first.toString()
                        }
                        draft = draft.copy(displayName = capitalized.take(24))
                    },
                    label = { Text("Имя ребёнка") },
                    placeholder = if (!nameFocused) {
                        { Text("Ребёнок", color = LlColors.TextSecondary) }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            nameFocused = it.isFocused
                            if (!it.isFocused && !nameValid) {
                                nameErrorShown = true
                            }
                        },
                    singleLine = true,
                    isError = nameErrorShown && !nameValid,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        cursorColor = Color.Black,
                        focusedLabelColor = Color.Black,
                        unfocusedLabelColor = Color.Black,
                        focusedBorderColor = Color.Black,
                        unfocusedBorderColor = Color.Black,
                        errorTextColor = Color.Black,
                        errorBorderColor = LlColors.Coral
                    )
                )
                if (!nameValid) {
                    Text("Введите имя минимум из двух символов", color = LlColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }

                Text("Возраст: ${draft.ageYears}", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = draft.ageYears.toFloat(),
                    onValueChange = {
                        draft = draft.copy(ageYears = it.roundToInt().coerceIn(5, 16))
                    },
                    valueRange = 5f..16f,
                    steps = 10,
                    colors = SliderDefaults.colors(thumbColor = LlColors.MintDeep, activeTrackColor = LlColors.Mint)
                )

                Text("Сложность: ${difficultyLabel(draft.difficultyOffset)}", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = draft.difficultyOffset.toFloat(),
                    onValueChange = {
                        draft = draft.copy(difficultyOffset = it.roundToInt().coerceIn(-1, 1))
                    },
                    valueRange = -1f..1f,
                    steps = 1,
                    colors = SliderDefaults.colors(thumbColor = LlColors.MintDeep, activeTrackColor = LlColors.Mint)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Легче", style = MaterialTheme.typography.bodySmall, color = LlColors.TextSecondary)
                    Text("Норма", style = MaterialTheme.typography.bodySmall, color = LlColors.TextSecondary)
                    Text("Сложнее", style = MaterialTheme.typography.bodySmall, color = LlColors.TextSecondary)
                }

                Text("Цвет фона", style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    backgroundChoices.forEachIndexed { index, (label, color) ->
                        FilterChip(
                            selected = draft.backgroundColor == index,
                            onClick = { draft = draft.copy(backgroundColor = index) },
                            label = { Text(label, maxLines = 1) },
                            leadingIcon = { androidx.compose.foundation.layout.Box(Modifier.size(14.dp).background(color, RoundedCornerShape(50))) },
                            colors = backgroundChipColors
                        )
                    }
                }

                Text("Запускать каждые: ${draft.launchIntervalMinutes} мин", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = draft.launchIntervalMinutes.toFloat(),
                    onValueChange = {
                        draft = draft.copy(launchIntervalMinutes = it.roundToInt().coerceIn(5, 60))
                    },
                    valueRange = 5f..60f,
                    steps = 10,
                    colors = SliderDefaults.colors(thumbColor = LlColors.MintDeep, activeTrackColor = LlColors.Mint)
                )

                Text("Выполнить ${draft.tasksToUnlock} заданий для разблокировки", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = draft.tasksToUnlock.toFloat(),
                    onValueChange = {
                        draft = draft.copy(tasksToUnlock = it.roundToInt().coerceIn(1, 20))
                    },
                    valueRange = 1f..20f,
                    steps = 18,
                    colors = SliderDefaults.colors(thumbColor = LlColors.MintDeep, activeTrackColor = LlColors.Mint)
                )
                if (nextLaunchAtMillis > 0L) {
                    Text(
                        "Следующий запуск: ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(nextLaunchAtMillis))}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (draft.lockEnabled) {
                    LlGhostButton("Права Android", onClick = onOpenPermissions)
                }

                Text("Окно: ${draft.activeWindowStartHour}:00–${draft.activeWindowEndHour}:00")
                Slider(
                    value = draft.activeWindowStartHour.toFloat(),
                    onValueChange = {
                        draft = draft.copy(activeWindowStartHour = it.roundToInt().coerceIn(0, 23))
                    },
                    valueRange = 0f..23f,
                    steps = 22,
                    colors = SliderDefaults.colors(thumbColor = LlColors.MintDeep, activeTrackColor = LlColors.Mint)
                )
                Slider(
                    value = draft.activeWindowEndHour.toFloat(),
                    onValueChange = {
                        draft = draft.copy(activeWindowEndHour = it.roundToInt().coerceIn(0, 23))
                    },
                    valueRange = 0f..23f,
                    steps = 22,
                    colors = SliderDefaults.colors(thumbColor = LlColors.MintDeep, activeTrackColor = LlColors.Mint)
                )

                Text("Вне окна")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = draft.outsideWindowMode == OutsideWindowMode.NO_TASKS.name,
                        onClick = {
                            draft = draft.copy(outsideWindowMode = OutsideWindowMode.NO_TASKS.name)
                        },
                        label = {
                            Text(
                                "Без задач",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = chipColors
                    )
                    FilterChip(
                        selected = draft.outsideWindowMode == OutsideWindowMode.HARD_BLOCK.name,
                        onClick = {
                            draft = draft.copy(outsideWindowMode = OutsideWindowMode.HARD_BLOCK.name)
                        },
                        label = {
                            Text(
                                "Полный запрет",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = chipColors
                    )
                }

                CategoryToggle("Математика", draft.mathEnabled) {
                    draft = draft.copy(mathEnabled = it)
                }
                CategoryToggle("Чтение", draft.readingEnabled) {
                    draft = draft.copy(readingEnabled = it)
                }
                CategoryToggle("Логика", draft.logicEnabled) {
                    draft = draft.copy(logicEnabled = it)
                }
                CategoryToggle("Подсказки", draft.hintsEnabled) {
                    draft = draft.copy(hintsEnabled = it)
                }

                Text("Ошибок до упрощения: ${draft.maxWrongBeforeEasier}")
                Slider(
                    value = draft.maxWrongBeforeEasier.toFloat(),
                    onValueChange = {
                        draft = draft.copy(maxWrongBeforeEasier = it.roundToInt().coerceIn(2, 5))
                    },
                    valueRange = 2f..5f,
                    steps = 2,
                    colors = SliderDefaults.colors(thumbColor = LlColors.MintDeep, activeTrackColor = LlColors.Mint)
                )
            }

            LlPrimaryButton("Сохранить", onClick = { onSave(draft.copy(displayName = draft.displayName.trim())) }, enabled = nameValid, coral = true, elevated = true)
            LlGhostButton("Изменить PIN", onClick = onChangePin)
            LlGhostButton("Сбросить PIN", onClick = { resetDialogVisible = true }, contentColor = LlColors.TextSecondary)
            LlGhostButton("Назад", onClick = onBack, contentColor = LlColors.TextSecondary, elevated = true)
            if (resetDialogVisible) {
                AlertDialog(
                    onDismissRequest = { resetDialogVisible = false },
                    title = { Text("Сбросить PIN родителя?") },
                    text = { Text("После сброса потребуется создать новый PIN.") },
                    confirmButton = {
                        TextButton(onClick = { resetDialogVisible = false; onResetPin() }) { Text("Сбросить") }
                    },
                    dismissButton = {
                        TextButton(onClick = { resetDialogVisible = false }) { Text("Отмена") }
                    }
                )
            }
            }
        }
    }
}

/** Строка «лейбл + Switch» для включения категории задач / подсказок. */
@Composable
private fun CategoryToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = LlColors.MintDeep,
                checkedThumbColor = LlColors.SurfaceElevated
            )
        )
    }
}

private fun difficultyLabel(offset: Int): String = when (offset) {
    -1 -> "Легче"
    1 -> "Сложнее"
    else -> "Норма"
}
