package com.learnlock.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.learnlock.app.ui.components.LlBackground
import com.learnlock.app.ui.components.LlGhostButton
import com.learnlock.app.ui.components.LlPanel
import com.learnlock.app.ui.components.LlPinField
import com.learnlock.app.ui.components.LlPrimaryButton
import com.learnlock.app.ui.theme.LlColors

/** Короткий онбординг: объяснение продукта → PIN → права Android. */
@Composable
fun OnboardingScreen(
    backgroundColor: Int = 0,
    onContinueToSettings: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    // На первом запуске PIN необязателен, но введённый PIN должен быть валидным.
    val pinValid = pin.isBlank() || pin.length in 4..12
    LlBackground(backgroundColor = backgroundColor) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Добро пожаловать", style = MaterialTheme.typography.displayMedium, color = LlColors.MintDeep)
            LlPanel {
                Text(
                    "Данное приложение помогает детям тренировать математику, русский язык, чтение и логику. Родитель может настроить блокировку и расписание заданий.",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                    modifier = Modifier.fillMaxWidth(),
                    softWrap = true
                )
                Spacer(Modifier.height(12.dp))
                LlPinField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = "PIN родителя (необязательно)",
                    labelStyle = MaterialTheme.typography.labelSmall
                )
                if (!pinValid) {
                    Text(
                        "PIN должен содержать от 4 до 12 цифр или быть пустым.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LlColors.Coral
                    )
                }
            }
            LlPrimaryButton("Далее", onClick = { onContinueToSettings(pin) }, enabled = pinValid)
        }
    }
}
