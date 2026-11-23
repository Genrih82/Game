package com.learnlock.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.learnlock.app.ui.components.LlPinField
import com.learnlock.app.ui.components.LlPrimaryButton
import com.learnlock.app.ui.theme.LlColors

/** Установка родительского PIN (4–12 цифр) при первом запуске. */
@Composable
fun ParentPinSetupScreen(
    backgroundColor: Int = 0,
    onSave: (String) -> Unit,
    onBack: () -> Unit,
    allowEmpty: Boolean = false
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = (allowEmpty && pin.isEmpty() && confirm.isEmpty()) ||
        (pin.length in 4..12 && pin == confirm && pin.all { it.isDigit() })

    LlBackground(backgroundColor = backgroundColor) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("PIN родителя", style = MaterialTheme.typography.displayMedium, color = LlColors.MintDeep)
            Text(
                "Хранится только локально в виде хеша.",
                style = MaterialTheme.typography.bodyMedium,
                color = LlColors.TextSecondary
            )
            LlPinField(value = pin, onValueChange = { pin = it }, label = if (allowEmpty) "PIN (можно оставить пустым)" else "PIN (4–12 цифр)")
            LlPinField(value = confirm, onValueChange = { confirm = it }, label = "Повтор PIN")
            LlPrimaryButton("Сохранить", onClick = { onSave(pin) }, enabled = valid, coral = true)
            LlGhostButton("Назад", onClick = onBack)
        }
    }
}
