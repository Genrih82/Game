package com.learnlock.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.learnlock.app.ui.theme.LlColors

/**
 * Поле PIN: светлый контейнер, тёмный текст; кнопка «глаз» показывает цифры.
 */
@Composable
internal fun LlPinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    labelStyle: TextStyle = MaterialTheme.typography.bodySmall
) {
    val ink = LlColors.Ink
    val fill = LlColors.SurfaceElevated
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            if (next.length <= 12 && next.all(Char::isDigit)) onValueChange(next)
        },
        label = { Text(label, style = labelStyle, color = LlColors.TextSecondary) },
        singleLine = true,
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Скрыть PIN" else "Показать PIN",
                    tint = LlColors.MintDeep
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = ink),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = ink,
            unfocusedTextColor = ink,
            disabledTextColor = ink,
            errorTextColor = ink,
            focusedContainerColor = fill,
            unfocusedContainerColor = fill,
            disabledContainerColor = fill,
            errorContainerColor = fill,
            focusedLabelColor = LlColors.MintDeep,
            unfocusedLabelColor = LlColors.TextSecondary,
            focusedBorderColor = LlColors.MintDeep,
            unfocusedBorderColor = LlColors.TextSecondary,
            errorBorderColor = LlColors.Coral,
            cursorColor = LlColors.MintDeep,
            selectionColors = TextSelectionColors(
                handleColor = LlColors.MintDeep,
                backgroundColor = LlColors.MintBright.copy(alpha = 0.35f)
            )
        )
    )
}
