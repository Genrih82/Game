package com.learnlock.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.learnlock.app.BuildConfig
import com.learnlock.app.ui.components.LlBackground
import com.learnlock.app.ui.components.LlGhostButton
import com.learnlock.app.ui.components.LlPanel
import com.learnlock.app.ui.components.LlPrimaryButton
import com.learnlock.app.ui.theme.LlColors

/** Форма обращения: сообщение, контактный email и необязательный скриншот. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedbackScreen(
    backgroundColor: Int = 0,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val subjects = listOf("Ошибка", "Идея", "Другое")
    var subject by remember { mutableStateOf(subjects.first()) }
    var message by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var screenshot by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        screenshot = uri
    }
    val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    LlBackground(backgroundColor = backgroundColor) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Обратная связь", style = MaterialTheme.typography.displayMedium, color = LlColors.MintDeep)
            LlPanel {
                Text(
                    "Тема обращения",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    subjects.forEach { option ->
                        FilterChip(
                            selected = subject == option,
                            onClick = { subject = option },
                            label = { Text(option, color = Color.Black) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LlColors.Mint,
                                selectedLabelColor = Color.Black,
                                labelColor = Color.Black
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(4000) },
                    label = { Text("Сообщение или предложение") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedLabelColor = Color.Black,
                        unfocusedLabelColor = Color.Black,
                        cursorColor = Color.Black
                    )
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.take(120) },
                    label = { Text("Email для ответа (необязательно)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedLabelColor = Color.Black,
                        unfocusedLabelColor = Color.Black,
                        cursorColor = Color.Black
                    )
                )
                LlGhostButton(
                    text = if (screenshot == null) "Добавить скриншот" else "Скриншот добавлен",
                    onClick = { picker.launch("image/*") }
                )
                Text(
                    "Устройство: $deviceInfo\nВерсия приложения: ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = LlColors.TextSecondary
                )
                LlPrimaryButton(
                    text = "Отправить",
                    enabled = message.isNotBlank(),
                    onClick = {
                        val body = buildString {
                            appendLine(message.trim())
                            if (email.isNotBlank()) appendLine("\nEmail для ответа: ${email.trim()}")
                            appendLine("\nУстройство: $deviceInfo")
                            appendLine("Версия приложения: ${BuildConfig.VERSION_NAME}")
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = if (screenshot != null) "image/*" else "text/plain"
                            putExtra(Intent.EXTRA_EMAIL, arrayOf("genrihmgs@mail.ru"))
                            putExtra(Intent.EXTRA_SUBJECT, subject.ifBlank { "Обратная связь LearnLock" })
                            putExtra(Intent.EXTRA_TEXT, body)
                            screenshot?.let {
                                putExtra(Intent.EXTRA_STREAM, it)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        }
                        context.startActivity(Intent.createChooser(intent, "Отправить обращение"))
                    },
                    coral = true
                )
            }
            LlGhostButton("Назад", onClick = onBack)
        }
    }
}
