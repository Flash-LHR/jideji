package com.hackerli.jizhang.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hackerli.jizhang.data.Expense
import com.hackerli.jizhang.data.PendingPhoto
import com.hackerli.jizhang.data.PhotoStorage
import com.hackerli.jizhang.data.QuickTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

@Composable
internal fun EditExpenseScreen(
    expense: Expense,
    tags: List<QuickTag>,
    operationInFlight: Boolean,
    modifier: Modifier = Modifier,
    onLaunchExternalActivity: () -> Unit,
    onCancel: () -> Unit,
    onSave: (Long, QuickTag, String, List<String>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val originalPaths = remember(expense.id) { expense.photos.map { it.path }.toSet() }
    var selectedTagId by remember(expense.id) { mutableLongStateOf(expense.tagId) }
    var amountYuan by remember(expense.id) { mutableLongStateOf(expense.amountCents / 100L) }
    var note by remember(expense.id) { mutableStateOf(expense.note) }
    var photoPaths by remember(expense.id) { mutableStateOf(expense.photos.map { it.path }) }
    var pendingCamera by remember { mutableStateOf<PendingPhoto?>(null) }
    var chooseTag by remember { mutableStateOf(false) }
    var editAmount by remember { mutableStateOf(false) }
    var editNote by remember { mutableStateOf(false) }
    var inlineError by remember { mutableStateOf<String?>(null) }
    var galleryImporting by remember { mutableStateOf(false) }
    val interactionLocked = operationInFlight || galleryImporting || pendingCamera != null
    val selectedTag = tags.firstOrNull { it.id == selectedTagId } ?: tags.firstOrNull()

    fun cancel() {
        if (interactionLocked) return
        PhotoStorage.deleteAll(photoPaths.filterNot { it in originalPaths })
        PhotoStorage.delete(pendingCamera?.path)
        onCancel()
    }
    BackHandler { if (!interactionLocked) cancel() }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val pending = pendingCamera
        if (saved && pending != null) photoPaths = photoPaths + pending.path else PhotoStorage.delete(pending?.path)
        pendingCamera = null
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            galleryImporting = true
            try {
                val result = withContext(Dispatchers.IO) {
                    PhotoStorage.copyFromGallery(context, uris, photoPaths)
                }
                result.onSuccess { photoPaths = photoPaths + it }
                    .onFailure { inlineError = "照片读取失败，请重试" }
            } finally {
                galleryImporting = false
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = ::cancel, enabled = !interactionLocked, contentPadding = PaddingValues(0.dp)) { Text("取消") }
            Spacer(Modifier.weight(1f))
            Text("编辑账单", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { if (selectedTag != null) onSave(amountYuan, selectedTag, note, photoPaths) },
                enabled = !interactionLocked && selectedTag != null && amountYuan * 100L >= expense.refundedAmountCents,
                contentPadding = PaddingValues(0.dp),
            ) { Text("保存") }
        }

        EditRow("标签", selectedTag?.let { "${it.emoji} ${it.name}" }.orEmpty(), !interactionLocked) { chooseTag = true }
        EditRow("金额", "¥${formatYuan(amountYuan)}", !interactionLocked) { editAmount = true }
        EditRow("备注", note.ifBlank { "未填写" }, !interactionLocked) { editNote = true }

        Text("照片", modifier = Modifier.padding(top = 18.dp), style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    runCatching { PhotoStorage.createCameraTarget(context) }
                        .onSuccess {
                            pendingCamera = it
                            onLaunchExternalActivity()
                            cameraLauncher.launch(it.uri)
                        }
                        .onFailure { inlineError = "无法打开相机" }
                },
                enabled = !interactionLocked,
                modifier = Modifier.weight(1f),
            ) { Text("拍照") }
            OutlinedButton(
                onClick = {
                    onLaunchExternalActivity()
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !interactionLocked,
                modifier = Modifier.weight(1f),
            ) { Text("相册") }
        }
        if (photoPaths.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(92.dp).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(photoPaths, key = { _, path -> path }) { index, path ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PhotoThumbnail(path, Modifier.size(64.dp))
                        TextButton(
                            onClick = {
                                if (path !in originalPaths) PhotoStorage.delete(path)
                                photoPaths = photoPaths.toMutableList().also { it.removeAt(index) }
                            },
                            enabled = !interactionLocked,
                            modifier = Modifier.height(22.dp),
                            contentPadding = PaddingValues(0.dp),
                        ) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        Text("时间", modifier = Modifier.padding(top = 18.dp), style = MaterialTheme.typography.labelLarge)
        Text(expense.fullTimeText(ZoneId.systemDefault()))
        Text("位置", modifier = Modifier.padding(top = 18.dp), style = MaterialTheme.typography.labelLarge)
        Text(expense.locationLabel.ifBlank { "位置已记录" })
        inlineError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp)) }
    }

    if (chooseTag) {
        AlertDialog(
            onDismissRequest = { chooseTag = false },
            title = { Text("选择标签") },
            text = {
                LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                    items(tags, key = { it.id }) { tag ->
                        TextButton(
                            onClick = { selectedTagId = tag.id; chooseTag = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("${tag.emoji} ${tag.name}", modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = {},
        )
    }
    if (editAmount) {
        var input by remember(amountYuan) { mutableStateOf(amountYuan.toString()) }
        AlertDialog(
            onDismissRequest = { editAmount = false },
            title = { Text("修改金额") },
            text = {
                Column {
                    Text("¥${formatYuan(MoneyInput.toYuan(input))}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    AmountKeypad(
                        input = input,
                        confirmEnabled = MoneyInput.toYuan(input) > 0 && MoneyInput.toYuan(input) * 100L >= expense.refundedAmountCents,
                        onInputChange = { input = it },
                        onConfirm = { amountYuan = MoneyInput.toYuan(input); editAmount = false },
                    )
                    if (MoneyInput.toYuan(input) * 100L < expense.refundedAmountCents) {
                        Text("原消费金额不能低于累计退款", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {},
        )
    }
    if (editNote) {
        var draft by remember(note) { mutableStateOf(note) }
        AlertDialog(
            onDismissRequest = { editNote = false },
            title = { Text("修改备注") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 200) draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                )
            },
            confirmButton = { TextButton(onClick = { note = draft.trim(); editNote = false }) { Text("保存备注") } },
            dismissButton = { TextButton(onClick = { editNote = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun EditRow(label: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Text(label)
        Spacer(Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(">")
    }
}
