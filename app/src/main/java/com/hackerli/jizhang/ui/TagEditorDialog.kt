package com.hackerli.jizhang.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.hackerli.jizhang.data.QuickTag
import com.hackerli.jizhang.data.TagImageStorage
import com.hackerli.jizhang.data.TagPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

private enum class TagVisualMode { EMOJI, IMAGE }

private val commonEmojis = listOf("🍔", "🍗", "🍜", "🍚", "☕", "🛒", "🚕", "🚌", "🚇", "🏠", "💊", "🎮")

@Composable
internal fun TagEditorDialog(
    title: String,
    tag: QuickTag?,
    onDismiss: () -> Unit,
    onLaunchExternalActivity: () -> Unit = {},
    onConfirm: (String, String, String?, Int) -> Boolean,
) {
    val context = LocalContext.current
    val originalImagePath = tag?.imagePath
    var name by remember(tag?.id) { mutableStateOf(tag?.name.orEmpty()) }
    var emoji by remember(tag?.id) { mutableStateOf(tag?.emoji.orEmpty()) }
    var imagePath by remember(tag?.id) { mutableStateOf(originalImagePath) }
    var visualMode by remember(tag?.id) {
        mutableStateOf(if (originalImagePath == null) TagVisualMode.EMOJI else TagVisualMode.IMAGE)
    }
    var color by remember(tag?.id) { mutableIntStateOf(tag?.colorArgb ?: TagPalette.colors.first()) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    var committed by remember(tag?.id) { mutableStateOf(false) }

    DisposableEffect(tag?.id) {
        onDispose {
            if (!committed && imagePath != originalImagePath) TagImageStorage.delete(context, imagePath)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) cropUri = uri
    }

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
                Text("标签图标", modifier = Modifier.padding(top = 13.dp), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = visualMode == TagVisualMode.EMOJI,
                        onClick = { visualMode = TagVisualMode.EMOJI },
                        label = { Text("Emoji") },
                    )
                    FilterChip(
                        selected = visualMode == TagVisualMode.IMAGE,
                        onClick = { visualMode = TagVisualMode.IMAGE },
                        label = { Text("图片") },
                    )
                }

                if (visualMode == TagVisualMode.EMOJI) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it.take(4) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Emoji") },
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        commonEmojis.forEach { option ->
                            TextButton(onClick = { emoji = option }, modifier = Modifier.size(38.dp)) { Text(option) }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (imagePath == null) {
                            Box(
                                Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) { Text("图片") }
                        } else {
                            TagIcon(
                                emoji = emoji.ifBlank { "●" },
                                imagePath = imagePath,
                                colorArgb = color,
                                contentDescription = "标签图片预览",
                                modifier = Modifier.size(64.dp),
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                onLaunchExternalActivity()
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        ) { Text(if (imagePath == null) "从相册选择" else "重新选择") }
                    }
                }

                Text("颜色", modifier = Modifier.padding(top = 13.dp), style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    TagPalette.colors.forEach { option ->
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(option))
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
                    val savedPath = imagePath.takeIf { visualMode == TagVisualMode.IMAGE }
                    if (onConfirm(name.trim(), emoji.trim().ifEmpty { "●" }, savedPath, color)) {
                        if (savedPath == null && imagePath != originalImagePath) {
                            TagImageStorage.delete(context, imagePath)
                        }
                        committed = true
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank() && (visualMode == TagVisualMode.EMOJI || imagePath != null),
            ) { Text("完成") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    cropUri?.let { uri ->
        TagImageCropDialog(
            uri = uri,
            onDismiss = { cropUri = null },
            onSaved = { path ->
                if (imagePath != originalImagePath) TagImageStorage.delete(context, imagePath)
                imagePath = path
                visualMode = TagVisualMode.IMAGE
                cropUri = null
            },
        )
    }
}

@Composable
private fun TagImageCropDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var error by remember(uri) { mutableStateOf<String?>(null) }
    var saving by remember(uri) { mutableStateOf(false) }
    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    var viewport by remember(uri) { mutableStateOf(IntSize.Zero) }

    fun clamp(candidate: Offset, scale: Float): Offset {
        val source = bitmap ?: return Offset.Zero
        val size = viewport.width.toFloat()
        if (size <= 0f) return Offset.Zero
        val baseScale = max(size / source.width, size / source.height)
        val maxX = ((source.width * baseScale * scale - size) / 2f).coerceAtLeast(0f)
        val maxY = ((source.height * baseScale * scale - size) / 2f).coerceAtLeast(0f)
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextZoom = (zoom * zoomChange).coerceIn(1f, 5f)
        zoom = nextZoom
        offset = clamp(offset + panChange, nextZoom)
    }

    LaunchedEffect(uri) {
        runCatching { withContext(Dispatchers.IO) { TagImageStorage.decode(context, uri) } }
            .onSuccess { bitmap = it }
            .onFailure { error = "图片读取失败，请重新选择" }
    }
    DisposableEffect(bitmap) {
        val ownedBitmap = bitmap
        onDispose { ownedBitmap?.recycle() }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("裁剪标签图片") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    error != null -> Text(requireNotNull(error), color = MaterialTheme.colorScheme.error)
                    bitmap == null -> Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    else -> {
                        Box(
                            modifier = Modifier.size(260.dp).clip(RoundedCornerShape(18.dp)).background(Color.Black)
                                .onSizeChanged {
                                    viewport = it
                                    offset = clamp(offset, zoom)
                                }
                                .transformable(transformState),
                        ) {
                            Image(
                                bitmap = requireNotNull(bitmap).asImageBitmap(),
                                contentDescription = "拖动和缩放图片",
                                modifier = Modifier.fillMaxSize().graphicsLayer(
                                    scaleX = zoom,
                                    scaleY = zoom,
                                    translationX = offset.x,
                                    translationY = offset.y,
                                ),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Text(
                            "拖动调整位置，双指缩放",
                            modifier = Modifier.padding(top = 10.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val source = bitmap ?: return@TextButton
                    scope.launch {
                        saving = true
                        runCatching {
                            withContext(Dispatchers.IO) {
                                TagImageStorage.saveCrop(
                                    context = context,
                                    source = source,
                                    zoom = zoom,
                                    offsetXFraction = offset.x / viewport.width.coerceAtLeast(1),
                                    offsetYFraction = offset.y / viewport.height.coerceAtLeast(1),
                                )
                            }
                        }.onSuccess(onSaved).onFailure { error = "图片保存失败，请重试" }
                        saving = false
                    }
                },
                enabled = bitmap != null && error == null && !saving,
            ) { Text(if (saving) "保存中…" else "使用图片") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } },
    )
}
