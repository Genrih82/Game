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
    hasPin: Boolean,
    backgroundColor: Int = 0,
    onContinueToPin: () -> Unit,
    onContinueToPermissions: () -> Unit,
    onDone: () -> Unit
) {
    LlBackground(backgroundColor = backgroundColor) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Как это работает", style = MaterialTheme.typography.displayMedium, color = LlColors.MintDeep)
            LlPanel {
                Text("1. После сна появляется задача", style = MaterialTheme.typography.bodyLarge)
                Text("2. Нужное число верных ответов открывает телефон до следующего запуска", style = MaterialTheme.typography.bodyLarge)
                Text("3. Родитель всегда может разблокировать PIN", style = MaterialTheme.typography.bodyLarge)
                Text("4. Нужны права специальных возможностей и автозапуск", style = MaterialTheme.typography.bodyLarge)
            }
            if (!hasPin) {
                LlPrimaryButton("Создать PIN родителя", onClick = onContinueToPin, coral = true)
            } else {
                LlPrimaryButton("Проверить права", onClick = onContinueToPermissions)
            }
            LlGhostButton("Закрыть", onClick = onDone)
        }
    }
}
