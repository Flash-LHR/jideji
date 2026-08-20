package com.hackerli.jizhang.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hackerli.jizhang.data.Expense
import com.hackerli.jizhang.data.LocationSnapshot
import com.hackerli.jizhang.data.LocationState
import com.hackerli.jizhang.data.QuickTag
import com.hackerli.jizhang.data.TagPredictor

@Composable
internal fun TagSelectionScreen(
    tags: List<QuickTag>,
    expenses: List<Expense>,
    locationState: LocationState,
    modifier: Modifier = Modifier,
    onRetryLocation: () -> Unit,
    onAddTag: (String, String, String?, Int) -> Boolean,
    onLaunchExternalActivity: () -> Unit,
    onSelect: (QuickTag, LocationSnapshot) -> Unit,
) {
    var addingTag by remember { mutableStateOf(false) }
    val location = (locationState as? LocationState.Ready)?.location
    val predicted = remember(tags, expenses, location) {
        if (location == null) emptyList() else TagPredictor.suggest(tags, expenses, location)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column {
            Text("记得记", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            LocationStatus(locationState, onRetryLocation)
        }

        Text(
            "猜你要记",
            modifier = Modifier.padding(top = 14.dp, bottom = 7.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (location == null) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("定位完成后显示推荐标签", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                predicted.forEach { tag ->
                    TagTile(tag, enabled = true, modifier = Modifier.weight(1f)) { onSelect(tag, location) }
                }
                repeat((3 - predicted.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
            }
        }

        Text(
            "全部标签",
            modifier = Modifier.padding(top = 16.dp, bottom = 7.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(tags, key = { it.id }) { tag ->
                TagTile(tag, enabled = location != null) {
                    if (location != null) onSelect(tag, location)
                }
            }
            item(key = "new-tag") {
                Surface(
                    onClick = { addingTag = true },
                    modifier = Modifier.height(70.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("＋", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                        Text("新建标签", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }

    if (addingTag) {
        TagEditorDialog(
            title = "新建标签",
            tag = null,
            onDismiss = { addingTag = false },
            onLaunchExternalActivity = onLaunchExternalActivity,
        ) { name, emoji, imagePath, color ->
            onAddTag(name, emoji, imagePath, color)
        }
    }
}

@Composable
private fun LocationStatus(state: LocationState, onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            LocationState.PermissionRequired -> Text("需要精确位置", color = MaterialTheme.colorScheme.error)
            LocationState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.width(13.dp).height(13.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
                Text("定位中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is LocationState.Ready -> Text(
                text = state.location.label.ifBlank { "位置已记录" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            is LocationState.Error -> TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) {
                Text("定位失败，点击重试")
            }
        }
    }
}

@Composable
private fun TagTile(
    tag: QuickTag,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(70.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(tag.colorArgb).copy(alpha = if (enabled) 0.14f else 0.06f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            TagIcon(
                emoji = tag.emoji,
                imagePath = tag.imagePath,
                colorArgb = tag.colorArgb,
                contentDescription = tag.name,
                modifier = Modifier.size(32.dp),
            )
            Text(tag.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
        }
    }
}
