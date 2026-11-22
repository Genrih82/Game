package com.learnlock.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.learnlock.app.ui.components.LlBackground
import com.learnlock.app.ui.components.LlGhostButton
import com.learnlock.app.ui.components.LlPanel
import com.learnlock.app.ui.components.LlPrimaryButton
import com.learnlock.app.ui.theme.LlColors

/** Короткий онбординг: объяснение продукта → PIN → права Android. */
@Composable
fun OnboardingScreen(
    backgroundColor: Int = 0,
    onContinueToSettings: () -> Unit
) {
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
                Text("МОЗГ помогает детям тренировать математику, русский язык, чтение и логику. Родитель может настроить блокировку и задания.", style = MaterialTheme.typography.bodyLarge)
            }
            LlPrimaryButton("Продолжить", onClick = onContinueToSettings)
        }
    }
}
