package com.hackerli.jizhang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hackerli.jizhang.data.QuickTag
import com.hackerli.jizhang.data.TagPalette

@Composable
internal fun TagEditorDialog(
    title: String,
    tag: QuickTag?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int) -> Boolean,
) {
    var name by remember(tag?.id) { mutableStateOf(tag?.name.orEmpty()) }
    var emoji by remember(tag?.id) { mutableStateOf(tag?.emoji.orEmpty()) }
    var color by remember(tag?.id) { mutableIntStateOf(tag?.colorArgb ?: TagPalette.colors.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 8) name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("标签名称") },
                    singleLine = true,
                )
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(4) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Emoji（可选）") },
                    singleLine = true,
                )
                Text("颜色", modifier = Modifier.padding(top = 14.dp), style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    TagPalette.colors.forEach { option ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(option))
                                .clickable { color = option },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (color == option) Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (onConfirm(name.trim(), emoji.trim().ifEmpty { "●" }, color)) onDismiss()
                },
                enabled = name.isNotBlank(),
            ) { Text("完成") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
