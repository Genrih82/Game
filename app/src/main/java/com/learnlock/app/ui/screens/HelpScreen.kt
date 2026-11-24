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
import com.learnlock.app.BuildConfig
import com.learnlock.app.ui.components.LlBackground
import com.learnlock.app.ui.components.LlGhostButton
import com.learnlock.app.ui.components.LlPanel
import com.learnlock.app.ui.theme.LlColors

@Composable
fun HelpScreen(
    backgroundColor: Int = 0,
    onBack: () -> Unit,
    onOpenFeedback: () -> Unit = {}
) {
    LlBackground(backgroundColor = backgroundColor) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Справка", style = MaterialTheme.typography.displayMedium, color = LlColors.MintDeep)
            LlPanel {
                Text("LearnLock", style = MaterialTheme.typography.headlineSmall, color = LlColors.MintDeep)
                Text("Версия ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                Text("Дата сборки: ${BuildConfig.BUILD_DATE}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Приложение разработано для детей школьного возраста и их родителей. Оно помогает тренировать математику и русский язык, предлагая задания перед доступом к телефону.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = LlColors.TextPrimary
                )
            }
            LlGhostButton("Обратная связь", onClick = onOpenFeedback)
            LlGhostButton("Назад", onClick = onBack)
        }
    }
}
