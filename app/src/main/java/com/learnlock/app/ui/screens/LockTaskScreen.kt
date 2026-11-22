package com.learnlock.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnlock.app.ui.components.LlBackground
import com.learnlock.app.ui.components.LlBrandMark
import com.learnlock.app.ui.components.LlChoiceTile
import com.learnlock.app.ui.components.LlGhostButton
import com.learnlock.app.ui.components.LlPanel
import com.learnlock.app.ui.components.LlPinField
import com.learnlock.app.ui.components.LlPrimaryButton
import com.learnlock.app.ui.theme.LlColors
import com.learnlock.app.ui.viewmodel.LockUiState
import com.learnlock.taskengine.AnswerInputType
import kotlinx.coroutines.delay

/**
 * UI экрана учебной задачи поверх приложений.
 * Поддерживает NUMBER/TEXT/CHOICE, подсказку и ввод родительского PIN.
 */
@Composable
fun LockTaskScreen(
    state: LockUiState,
    onAnswer: (String) -> Unit,
    onUseHint: () -> Unit,
    onParentUnlock: (String) -> Unit,
    onDismissIfUnlocked: () -> Unit,
    onToggleParentPin: (Boolean) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenHelp: () -> Unit = {}
) {
    LaunchedEffect(state.unlocked) {
        if (state.unlocked) {
            delay(1100)
            onDismissIfUnlocked()
        }
    }

    var answer by remember(state.task?.id) { mutableStateOf("") }
    var parentPin by remember { mutableStateOf("") }

    LlBackground(energetic = true) {
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Готовим челлендж…",
                    style = MaterialTheme.typography.titleLarge,
                    color = LlColors.Mist
                )
            }
            return@LlBackground
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Настройки", tint = LlColors.Mist)
                }
                IconButton(onClick = onOpenHelp) {
                    Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "Справка", tint = LlColors.Mist)
                }
            }
            LlBrandMark(
                light = true,
                subtitle = "Реши задачу — и телефон снова твой"
            )
            Text(
                if (state.unlimited) {
                    val total = state.sessionCorrect + state.sessionWrong
                    "Всего: $total · верных: ${state.sessionCorrect} · неверных: ${state.sessionWrong}"
                } else {
                    "Решено: ${state.tasksSolved} из ${state.tasksRequired}"
                },
                color = LlColors.Mist,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            AnimatedContent(
                targetState = state.task?.id to state.unlocked,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut()
                },
                label = "task"
            ) { (taskId, unlocked) ->
                if (unlocked) {
                    LlPanel {
                        Text(
                            "Круто!",
                            style = MaterialTheme.typography.headlineLarge,
                            color = LlColors.MintDeep
                        )
                        Text(
                            state.feedback ?: "Доступ открыт",
                            style = MaterialTheme.typography.bodyLarge,
                            color = LlColors.TextSecondary
                        )
                    }
                } else {
                    val task = state.task
                    if (task != null && taskId == task.id) {
                        LlPanel {
                            Text(
                                task.category.displayNameRu(),
                                style = MaterialTheme.typography.labelMedium,
                                color = LlColors.MintDeep
                            )
                            Text(
                                task.prompt,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 17.sp,
                                    lineHeight = 24.sp
                                ),
                                color = LlColors.TextPrimary,
                                textAlign = TextAlign.Center,
                                softWrap = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            when (task.inputType) {
                                AnswerInputType.NUMBER, AnswerInputType.TEXT -> {
                                    OutlinedTextField(
                                        value = answer,
                                        onValueChange = { answer = it },
                                        label = { Text("Твой ответ") },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = if (task.inputType == AnswerInputType.NUMBER) {
                                                KeyboardType.Number
                                            } else {
                                                KeyboardType.Text
                                            }
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = state.cooldownSeconds == 0,
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = LlColors.Surface,
                                            unfocusedContainerColor = LlColors.Surface,
                                            focusedTextColor = LlColors.TextPrimary,
                                            unfocusedTextColor = LlColors.TextPrimary,
                                            focusedLabelColor = LlColors.MintDeep,
                                            unfocusedLabelColor = LlColors.TextSecondary,
                                            focusedBorderColor = LlColors.MintDeep,
                                            unfocusedBorderColor = LlColors.TextSecondary,
                                            cursorColor = LlColors.MintDeep
                                        )
                                    )
                                    LlPrimaryButton(
                                        text = "Проверить",
                                        onClick = { onAnswer(answer) },
                                        enabled = answer.isNotBlank() && state.cooldownSeconds == 0,
                                        coral = true
                                    )
                                }

                                AnswerInputType.CHOICE -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        task.choices.chunked(2).forEach { row ->
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                row.forEach { choice ->
                                                    LlChoiceTile(
                                                        label = choice.label,
                                                        selected = answer == choice.label,
                                                        enabled = state.cooldownSeconds == 0,
                                                        onClick = {
                                                            answer = choice.label
                                                            onAnswer(choice.label)
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                if (row.size == 1) Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }

                            if (state.profile.hintsEnabled) {
                                val steps = task.hintSteps()
                                val canRevealMore = state.hintLevel < steps.size ||
                                    (steps.isEmpty() && !state.hintUsed)
                                LlGhostButton(
                                    text = when {
                                        state.hintLevel == 0 -> "Подсказка"
                                        state.hintLevel < steps.size -> "Ещё подсказка"
                                        else -> "Подсказка"
                                    },
                                    onClick = onUseHint,
                                    enabled = canRevealMore,
                                    elevated = false
                                )
                            }
                            if (state.hintVisible && state.currentHint != null) {
                                Text(
                                    state.currentHint!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = LlColors.MintDeep
                                )
                            }
                        }
                    }
                }
            }

            state.feedback?.takeIf { !state.unlocked }?.let {
                Text(it, color = LlColors.CoralSoft, textAlign = TextAlign.Center)
            }
            if (state.cooldownSeconds > 0) {
                Text(
                    "Пауза ${state.cooldownSeconds} с…",
                    color = LlColors.MistDeep,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(Modifier.height(8.dp))
            if (!state.unlimited && !state.unlocked) {
                Text(
                    "Чтобы разблокировать телефон, решите верно ${state.tasksRequired} заданий.",
                    color = LlColors.MistDeep,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
            LlGhostButton(
                text = if (state.showParentPin) "Скрыть PIN родителя" else "Родительский PIN",
                onClick = { onToggleParentPin(!state.showParentPin) }
            )
            if (state.showParentPin) {
                LlPanel {
                    LlPinField(
                        value = parentPin,
                        onValueChange = { parentPin = it },
                        label = "PIN родителя"
                    )
                    LlPrimaryButton(
                        text = "Разблокировать",
                        onClick = { onParentUnlock(parentPin) },
                        enabled = parentPin.length >= 4
                    )
                }
            }
        }
    }
}
