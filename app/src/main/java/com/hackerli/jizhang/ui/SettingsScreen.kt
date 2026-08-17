package com.hackerli.jizhang.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hackerli.jizhang.data.QuickTag
import java.text.DecimalFormat

@Composable
internal fun SettingsScreen(
    photoBytes: Long,
    versionName: String,
    updateText: String,
    modifier: Modifier = Modifier,
    onOpenTags: () -> Unit,
    onExport: () -> Unit,
    onCheckUpdate: () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        SettingsGroup("标签") {
            SettingsRow("标签管理", "新建、排序、停用和恢复", onOpenTags)
        }
        SettingsGroup("数据") {
            SettingsRow("完整导出", "Excel 与全部原图", onExport)
            SettingsRow("照片占用空间", formatBytes(photoBytes), null)
        }
        SettingsGroup("权限") {
            SettingsRow("精确位置", "已开启", null)
        }
        SettingsGroup("关于") {
            SettingsRow("当前版本", versionName, null)
            SettingsRow("检查更新", updateText, onCheckUpdate)
            SettingsRow("数据存储", "账单和照片保存在本机", null)
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 22.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun SettingsRow(title: String, detail: String, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        if (onClick != null) {
            Spacer(Modifier.width(7.dp))
            Text(">", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider()
}

@Composable
internal fun TagManagementScreen(
    allTags: List<QuickTag>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onAdd: (String, String, Int) -> Boolean,
    onUpdate: (QuickTag) -> Boolean,
    onArchive: (QuickTag, Boolean) -> Unit,
    onMove: (QuickTag, Int) -> Unit,
) {
    val active = allTags.filterNot { it.isArchived }.sortedBy { it.sortOrder }
    val archived = allTags.filter { it.isArchived }.sortedBy { it.name }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<QuickTag?>(null) }
    var pendingArchive by remember { mutableStateOf<QuickTag?>(null) }
    BackHandler(onBack = onBack)

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("‹ 返回") }
            Spacer(Modifier.weight(1f))
            Text("标签管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Button(onClick = { adding = true }) { Text("新建") }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
            item { Text("有效标签", modifier = Modifier.padding(top = 14.dp, bottom = 5.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            itemsIndexed(active, key = { _, tag -> tag.id }) { index, tag ->
                TagManageRow(
                    tag = tag,
                    canMoveUp = index > 0,
                    canMoveDown = index < active.lastIndex,
                    onMove = { onMove(tag, it) },
                    onEdit = { editing = tag },
                    onArchive = { pendingArchive = tag },
                )
                HorizontalDivider()
            }
            if (archived.isNotEmpty()) {
                item { Text("已停用标签", modifier = Modifier.padding(top = 22.dp, bottom = 5.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(archived, key = { "archived-${it.id}" }) { tag ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${tag.emoji} ${tag.name}", modifier = Modifier.weight(1f))
                        TextButton(onClick = { onArchive(tag, false) }) { Text("恢复") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (adding) {
        TagEditorDialog("新建标签", null, onDismiss = { adding = false }) { name, emoji, color ->
            onAdd(name, emoji, color)
        }
    }
    editing?.let { tag ->
        TagEditorDialog("编辑标签", tag, onDismiss = { editing = null }) { name, emoji, color ->
            onUpdate(tag.copy(name = name, emoji = emoji, colorArgb = color))
        }
    }
    pendingArchive?.let { tag ->
        AlertDialog(
            onDismissRequest = { pendingArchive = null },
            title = { Text("停用“${tag.name}”？") },
            text = { Text("它不会再用于录单和预测，历史账单仍然保留。") },
            confirmButton = {
                TextButton(onClick = { onArchive(tag, true); pendingArchive = null }) { Text("停用") }
            },
            dismissButton = { TextButton(onClick = { pendingArchive = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun TagManageRow(
    tag: QuickTag,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
) {
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val canMoveUpNow by rememberUpdatedState(canMoveUp)
    val canMoveDownNow by rememberUpdatedState(canMoveDown)
    val onMoveNow by rememberUpdatedState(onMove)
    val dragThreshold = with(LocalDensity.current) { 38.dp.toPx() }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(tag.colorArgb).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) { Text(tag.emoji, fontSize = 22.sp) }
        Spacer(Modifier.width(10.dp))
        Text(tag.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Text(
            "≡",
            modifier = Modifier.padding(10.dp).pointerInput(tag.id, dragThreshold) {
                detectDragGestures(
                    onDragStart = { dragDistance = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        dragDistance += amount.y
                        if (dragDistance > dragThreshold && canMoveDownNow) {
                            onMoveNow(1)
                            dragDistance = 0f
                        }
                        if (dragDistance < -dragThreshold && canMoveUpNow) {
                            onMoveNow(-1)
                            dragDistance = 0f
                        }
                    },
                )
            },
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onEdit) { Text("编辑") }
        TextButton(onClick = onArchive) { Text("停用") }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1_024 -> "$bytes B"
    bytes < 1_024 * 1_024 -> "${bytes / 1_024} KB"
    bytes < 1_024L * 1_024 * 1_024 -> "${DecimalFormat("0.0").format(bytes / 1_048_576.0)} MB"
    else -> "${DecimalFormat("0.0").format(bytes / 1_073_741_824.0)} GB"
}
