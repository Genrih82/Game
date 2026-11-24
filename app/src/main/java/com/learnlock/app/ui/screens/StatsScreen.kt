package com.learnlock.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.learnlock.app.data.TaskAttemptEntity
import com.learnlock.app.data.AttemptTotals
import com.learnlock.app.ui.components.LlBackground
import com.learnlock.app.ui.components.LlGhostButton
import com.learnlock.app.ui.components.LlPanel
import com.learnlock.app.ui.theme.LlColors
import com.learnlock.taskengine.AttemptRecord
import com.learnlock.taskengine.LearnerStats
import com.learnlock.taskengine.SkillTag
import com.learnlock.taskengine.TaskCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Статистика попыток за 7 дней и сводка по навыкам (LearnerStats). */
@Composable
fun StatsScreen(
    attempts: List<TaskAttemptEntity>,
    totals: AttemptTotals = AttemptTotals(),
    backgroundColor: Int = 0,
    onBack: () -> Unit
) {
    val learning = attempts.filter { !it.usedParentUnlock }
    val subjectAccuracy = learning.groupBy { it.category }
        .toList()
        .sortedBy { it.first }
    val weakTopics = weakTopicsFromAttempts(learning).take(3)
    val formatter = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

    LlBackground(backgroundColor = backgroundColor) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Статистика", style = MaterialTheme.typography.displayMedium, color = LlColors.MintDeep)

            LlPanel {
                Text("Верных ответов: ${totals.correct}", style = MaterialTheme.typography.titleLarge, color = LlColors.MintDeep)
                Text("Неверных ответов: ${totals.incorrect}", style = MaterialTheme.typography.titleLarge, color = LlColors.Coral)
                Text("Всего примеров: ${totals.correct + totals.incorrect}", style = MaterialTheme.typography.titleLarge)
            }

            if (subjectAccuracy.isNotEmpty()) {
                LlPanel {
                    Text("Точность по предметам", style = MaterialTheme.typography.titleLarge)
                    subjectAccuracy.forEach { (category, subjectAttempts) ->
                        val correct = subjectAttempts.count { it.correct }
                        val accuracy = correct.toDouble() / subjectAttempts.size
                        Text(
                            "${categoryDisplayName(category)}: ${"%.0f".format(accuracy * 100)}% ($correct из ${subjectAttempts.size})",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            if (weakTopics.isNotEmpty()) {
                LlPanel {
                    Text("Слабые темы", style = MaterialTheme.typography.titleLarge, color = LlColors.Coral)
                    weakTopics.forEach { (name, acc, count) ->
                        Text(
                            "• $name — ${"%.0f".format(acc * 100)}% ($count)",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Text(
                        "Адаптивный режим чаще даёт эти темы.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LlColors.TextSecondary
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(attempts) { item ->
                    LlPanel {
                        Text(formatter.format(Date(item.timestampMillis)), style = MaterialTheme.typography.labelMedium)
                        val skillLabel = item.skillTag
                            .takeIf { it.isNotBlank() }
                            ?.let { runCatching { SkillTag.valueOf(it).displayNameRu() }.getOrNull() }
                        Text(
                            buildString {
                                append(item.category)
                                if (skillLabel != null) append(" · ").append(skillLabel)
                                append(" · ")
                                append(
                                    when {
                                        item.usedParentUnlock -> "PIN"
                                        item.correct -> "верно"
                                        else -> "ошибка"
                                    }
                                )
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
            LlGhostButton("Назад", onClick = onBack)
        }
    }
}

private fun categoryDisplayName(category: String): String = when (category.uppercase()) {
    "MATH", "МАТЕМАТИКА" -> "Математика"
    "RUSSIAN", "РУССКИЙ" -> "Русский язык"
    "READING", "ЧТЕНИЕ" -> "Чтение"
    "LOGIC", "ЛОГИКА" -> "Логика"
    else -> category
}

private fun weakTopicsFromAttempts(
    attempts: List<TaskAttemptEntity>
): List<Triple<String, Double, Int>> {
    val records = attempts.mapNotNull { entity ->
        val skill = entity.skillTag.takeIf { it.isNotBlank() }
            ?.let { runCatching { SkillTag.valueOf(it) }.getOrNull() }
            ?: return@mapNotNull null
        val category = runCatching { TaskCategory.valueOf(entity.category) }.getOrNull()
            ?: skill.category
        AttemptRecord(skillTag = skill, category = category, correct = entity.correct)
    }
    val stats = LearnerStats.fromAttempts(records.take(30))
    return stats.weakSkills().map { skill ->
        Triple(
            skill.displayNameRu(),
            stats.skillAccuracy[skill] ?: 0.0,
            stats.skillAttempts[skill] ?: 0
        )
    }
}
