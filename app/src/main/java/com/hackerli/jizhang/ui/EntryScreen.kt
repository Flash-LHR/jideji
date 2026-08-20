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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hackerli.jizhang.data.Expense
import com.hackerli.jizhang.data.LocationSnapshot
import com.hackerli.jizhang.data.PendingPhoto
import com.hackerli.jizhang.data.PhotoStorage
import com.hackerli.jizhang.data.PricePredictor
import com.hackerli.jizhang.data.QuickTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun EntryScreen(
    tag: QuickTag,
    location: LocationSnapshot,
    expenses: List<Expense>,
    operationInFlight: Boolean,
    modifier: Modifier = Modifier,
    onLaunchExternalActivity: () -> Unit,
    onBack: () -> Unit,
    onRecord: (Long, String, List<String>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var photoPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingCamera by remember { mutableStateOf<PendingPhoto?>(null) }
    var noteDialog by remember { mutableStateOf(false) }
    var inlineError by remember { mutableStateOf<String?>(null) }
    var galleryImporting by remember { mutableStateOf(false) }
    val interactionLocked = operationInFlight || galleryImporting || pendingCamera != null
    val predictions = remember(expenses, tag.id, location) {
        PricePredictor.suggestYuan(expenses, tag.id, location)
    }

    fun discardAndBack() {
        if (interactionLocked) return
        PhotoStorage.deleteAll(photoPaths)
        PhotoStorage.delete(pendingCamera?.path)
        onBack()
    }

    BackHandler { if (!interactionLocked) discardAndBack() }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val pending = pendingCamera
        if (saved && pending != null) photoPaths = photoPaths + pending.path else PhotoStorage.delete(pending?.path)
        pendingCamera = null
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
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
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = ::discardAndBack,
                enabled = !interactionLocked,
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) { Text("‹ 返回") }
            Spacer(Modifier.width(8.dp))
            TagIcon(tag.emoji, tag.imagePath, tag.colorArgb, tag.name, Modifier.size(36.dp))
            Spacer(Modifier.width(8.dp))
            Text(tag.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Text(
            text = location.label.ifBlank { "位置已记录" },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            OutlinedButton(
                onClick = { noteDialog = true },
                enabled = !interactionLocked,
                modifier = Modifier.weight(1f).height(42.dp),
                contentPadding = PaddingValues(0.dp),
            ) { Text(if (note.isBlank()) "备注" else "已备注") }
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
                modifier = Modifier.weight(1f).height(42.dp),
                contentPadding = PaddingValues(0.dp),
            ) { Text("拍照") }
            OutlinedButton(
                onClick = {
                    onLaunchExternalActivity()
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !interactionLocked,
                modifier = Modifier.weight(1f).height(42.dp),
                contentPadding = PaddingValues(0.dp),
            ) { Text("相册") }
        }

        if (photoPaths.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(66.dp).padding(top = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                itemsIndexed(photoPaths, key = { _, path -> path }) { index, path ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PhotoThumbnail(path, Modifier.size(48.dp))
                        TextButton(
                            onClick = {
                                PhotoStorage.delete(path)
                                photoPaths = photoPaths.toMutableList().also { it.removeAt(index) }
                            },
                            enabled = !interactionLocked,
                            modifier = Modifier.height(18.dp),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text("删除", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }

        Text(
            text = "¥${formatYuan(MoneyInput.toYuan(input))}",
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )

        Row(
            modifier = Modifier.fillMaxWidth().height(46.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            predictions.forEach { yuan ->
                Button(
                    onClick = { if (!interactionLocked) onRecord(yuan, note, photoPaths) },
                    enabled = !interactionLocked,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(0.dp),
                ) { Text("¥${formatYuan(yuan)}", fontWeight = FontWeight.Bold) }
            }
            repeat((3 - predictions.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
        }

        inlineError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.weight(1f))
        AmountKeypad(
            input = input,
            enabled = !interactionLocked,
            confirmEnabled = MoneyInput.toYuan(input) > 0,
            onInputChange = { input = it; inlineError = null },
            onConfirm = { onRecord(MoneyInput.toYuan(input), note, photoPaths) },
        )
    }

    if (noteDialog) {
        var draft by remember(note) { mutableStateOf(note) }
        AlertDialog(
            onDismissRequest = { noteDialog = false },
            title = { Text("备注") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 200) draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    placeholder = { Text("最多 200 个字") },
                )
            },
            confirmButton = {
                TextButton(onClick = { note = draft.trim(); noteDialog = false }) { Text("保存备注") }
            },
            dismissButton = { TextButton(onClick = { noteDialog = false }) { Text("取消") } },
        )
    }
}
