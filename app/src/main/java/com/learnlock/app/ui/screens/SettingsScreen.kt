package com.learnlock.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import com.learnlock.app.data.ChildProfileEntity
import com.learnlock.app.ui.components.LlBackground
import com.learnlock.app.ui.components.LlGhostButton
import com.learnlock.app.ui.components.LlPanel
import com.learnlock.app.ui.components.LlPrimaryButton
import com.learnlock.app.ui.screens.allRequiredPermissionsGranted
import com.learnlock.app.ui.theme.LlColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Редактирование профиля ребёнка: имя, фон, предметы и выключатель блокировки.
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
    onOpenPermissions: (ChildProfileEntity) -> Unit = {},
    onBack: () -> Unit
) {
    var draft by remember(profile) {
        mutableStateOf(profile.copy(displayName = profile.displayName.takeUnless { it == "Ребёнок" } ?: ""))
    }
    val nameValid = draft.displayName.trim().length >= 2
    var nameFocused by remember { mutableStateOf(false) }
    var nameErrorShown by remember { mutableStateOf(false) }
    var resetDialogVisible by remember { mutableStateOf(false) }
    var permissionDialogVisible by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var permissionsGranted by remember { mutableStateOf(allRequiredPermissionsGranted(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionsGranted = allRequiredPermissionsGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
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
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Настройки", style = MaterialTheme.typography.displayMedium, color = LlColors.MintDeep)

            LlPanel {
                OutlinedTextField(
                    value = draft.displayName,
                    onValueChange = { value ->
                        // В имени разрешены только буквы и пробелы.
                        val lettersAndSpaces = value.filter { it.isLetter() || it == ' ' }
                        val capitalized = lettersAndSpaces.replaceFirstChar { first ->
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
                if (nameErrorShown && !nameValid) {
                    Text(
                        "Необходимо ввести имя ребёнка (минимум 2 символа)",
                        color = LlColors.Coral,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text("Возраст: ${draft.ageYears} лет", style = MaterialTheme.typography.titleMedium)
                Slider(value = draft.ageYears.toFloat(), onValueChange = { draft = draft.copy(ageYears = it.toInt().coerceIn(5, 18)) }, valueRange = 5f..18f, steps = 12, colors = SliderDefaults.colors(thumbColor = LlColors.MintDeep, activeTrackColor = LlColors.Mint))

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

                Text("Упражнения", style = MaterialTheme.typography.titleLarge)
                CategoryToggle("Математика", draft.mathEnabled) { enabled ->
                    draft = draft.copy(
                        mathEnabled = keepCategory(
                            enabled,
                            draft.readingEnabled,
                            draft.logicEnabled,
                            draft.englishEnabled
                        )
                    )
                }
                CategoryToggle("Русский", draft.readingEnabled) { enabled ->
                    draft = draft.copy(
                        readingEnabled = keepCategory(
                            enabled,
                            draft.mathEnabled,
                            draft.logicEnabled,
                            draft.englishEnabled
                        )
                    )
                }
                CategoryToggle("Логика", draft.logicEnabled) { enabled ->
                    draft = draft.copy(
                        logicEnabled = keepCategory(
                            enabled,
                            draft.mathEnabled,
                            draft.readingEnabled,
                            draft.englishEnabled
                        )
                    )
                }
                CategoryToggle("Английский", draft.englishEnabled) { enabled ->
                    draft = draft.copy(
                        englishEnabled = keepCategory(
                            enabled,
                            draft.mathEnabled,
                            draft.readingEnabled,
                            draft.logicEnabled
                        )
                    )
                }
                CategoryToggle("Подсказки", draft.hintsEnabled) {
                    draft = draft.copy(hintsEnabled = it)
                }

                Text("Блокировка экрана", style = MaterialTheme.typography.titleLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (draft.lockEnabled) "Включена" else "Выключена",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = draft.lockEnabled,
                        onCheckedChange = { draft = draft.copy(lockEnabled = it) },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = LlColors.MintDeep,
                            checkedThumbColor = LlColors.SurfaceElevated
                        )
                    )
                }
                if (draft.lockEnabled) {
                    Text("Количество заданий: ${draft.tasksToUnlock}", style = MaterialTheme.typography.titleMedium)
                    Slider(value = draft.tasksToUnlock.toFloat(), onValueChange = { draft = draft.copy(tasksToUnlock = it.toInt().coerceIn(1, 20)) }, valueRange = 1f..20f, steps = 18, colors = SliderDefaults.colors(thumbColor = LlColors.MintDeep, activeTrackColor = LlColors.Mint))
                    Text("Время запуска: каждые ${draft.launchIntervalMinutes} мин", style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = draft.launchIntervalMinutes.toFloat(),
                        onValueChange = {
                            val snapped = (it / 5f).roundToInt() * 5
                            draft = draft.copy(launchIntervalMinutes = snapped.coerceIn(5, 60))
                        },
                        valueRange = 5f..60f,
                        steps = 10,
                        colors = SliderDefaults.colors(thumbColor = LlColors.MintDeep, activeTrackColor = LlColors.Mint)
                    )
                    LlGhostButton(
                        "Права Android",
                        onClick = {
                            if (nameValid) {
                                onOpenPermissions(draft.copy(displayName = draft.displayName.trim()))
                            } else {
                                nameErrorShown = true
                                scope.launch { scrollState.animateScrollTo(0) }
                            }
                        }
                    )
                    if (nextLaunchAtMillis > 0L) {
                        Text(
                            "Следующий запуск: ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(nextLaunchAtMillis))}",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            // Кнопка остаётся доступной: при пустом имени показываем ошибку и возвращаемся к полю.
            LlPrimaryButton(
                "Сохранить",
                onClick = {
                    if (nameValid) {
                        val saved = draft.copy(displayName = draft.displayName.trim())
                        if (saved.lockEnabled && !permissionsGranted) {
                            permissionDialogVisible = true
                        } else {
                            onSave(saved)
                        }
                    } else {
                        nameErrorShown = true
                        scope.launch { scrollState.animateScrollTo(0) }
                    }
                },
                coral = true,
                elevated = true
            )
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
            if (permissionDialogVisible) {
                AlertDialog(
                    onDismissRequest = { permissionDialogVisible = false },
                    title = { Text("Нужны разрешения Android") },
                    text = { Text("Для автоматического запуска заданий необходимо включить все разрешения Android.") },
                    confirmButton = {
                        TextButton(onClick = {
                            permissionDialogVisible = false
                            onOpenPermissions(draft.copy(displayName = draft.displayName.trim()))
                        }) { Text("Продолжить") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            permissionDialogVisible = false
                            onSave(draft.copy(displayName = draft.displayName.trim(), lockEnabled = false))
                        }) { Text("Отмена") }
                    }
                )
            }
        }
    }
}
}

private fun keepCategory(enabled: Boolean, vararg others: Boolean): Boolean {
    if (!enabled && others.none { it }) return true
    return enabled
}

@Composable
private fun IntStepper(value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(
            onClick = { onChange((value - 1).coerceIn(range)) },
            enabled = value > range.first,
            modifier = Modifier.widthIn(min = 72.dp)
        ) {
            Text("−", style = MaterialTheme.typography.headlineMedium, color = LlColors.MintDeep)
        }
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = LlColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        TextButton(
            onClick = { onChange((value + 1).coerceIn(range)) },
            enabled = value < range.last,
            modifier = Modifier.widthIn(min = 72.dp)
        ) {
            Text("+", style = MaterialTheme.typography.headlineMedium, color = LlColors.MintDeep)
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
