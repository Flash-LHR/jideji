package com.hackerli.jizhang.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.ui.graphics.Color

@Composable
internal fun AmountKeypad(
    input: String,
    confirmEnabled: Boolean,
    onInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AMOUNT_KEY_ROWS.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                row.forEach { key ->
                    val isConfirm = key == "确认"
                    if (isConfirm) {
                        Button(
                            onClick = onConfirm,
                            enabled = enabled && confirmEnabled,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text("确认", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                onInputChange(
                                    if (key == "退格") {
                                        MoneyInput.backspace(input)
                                    } else {
                                        MoneyInput.appendDigit(input, key.single())
                                    },
                                )
                            },
                            enabled = enabled,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(key, fontSize = if (key == "退格") 15.sp else 21.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

private val AMOUNT_KEY_ROWS = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf("退格", "0", "确认"),
)

@Composable
internal fun PhotoThumbnail(
    path: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    maxDimension: Int = 640,
) {
    val image = rememberFileImage(path, maxDimension)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = requireNotNull(image),
                contentDescription = "消费照片",
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else {
            Text("照片")
        }
    }
}

@Composable
internal fun TagIcon(
    emoji: String,
    imagePath: String?,
    colorArgb: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val image = rememberFileImage(imagePath, 384)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (imagePath == null) Color(colorArgb).copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(emoji, fontSize = 22.sp)
        }
    }
}

@Composable
private fun rememberFileImage(path: String?, maxDimension: Int): androidx.compose.ui.graphics.ImageBitmap? {
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path, maxDimension) {
        value = if (path.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    return image
}

@Composable
internal fun ZoomablePhoto(path: String, modifier: Modifier = Modifier) {
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offset by remember(path) { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { _, zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = nextScale
        offset = if (nextScale == 1f) Offset.Zero else offset + panChange
    }
    PhotoThumbnail(
        path = path,
        modifier = modifier
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y,
            )
            .transformable(state),
        contentScale = ContentScale.Fit,
        maxDimension = 2048,
    )
}

internal fun formatCents(cents: Long): String {
    val formatter = NumberFormat.getIntegerInstance(Locale.CHINA)
    return formatter.format(cents / 100L)
}

internal fun formatYuan(yuan: Long): String = NumberFormat.getIntegerInstance(Locale.CHINA).format(yuan)
