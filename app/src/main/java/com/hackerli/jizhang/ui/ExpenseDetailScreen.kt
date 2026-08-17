package com.hackerli.jizhang.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hackerli.jizhang.data.Expense
import com.hackerli.jizhang.data.Refund
import com.hackerli.jizhang.data.RefundStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun ExpenseDetailScreen(
    expense: Expense,
    operationInFlight: Boolean,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRefundRemaining: () -> Unit,
    onRecordRefund: () -> Unit,
    onDeleteRefund: (Long) -> Unit,
    onDeleteExpense: () -> Unit,
) {
    val zoneId = remember { ZoneId.systemDefault() }
    var confirmDeleteExpense by remember { mutableStateOf(false) }
    var pendingDeleteRefund by remember { mutableStateOf<Refund?>(null) }
    var photoViewerIndex by remember { mutableStateOf<Int?>(null) }
    BackHandler { if (!operationInFlight) onBack() }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, enabled = !operationInFlight, contentPadding = PaddingValues(0.dp)) {
                Text("‹ 返回")
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onEdit, enabled = !operationInFlight) { Text("编辑") }
        }
        Text("${expense.tagEmoji} ${expense.tagName}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("¥${formatCents(expense.actualAmountCents)}", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        when (expense.refundStatus) {
            RefundStatus.PARTIAL -> Text("部分退款", color = MaterialTheme.colorScheme.tertiary)
            RefundStatus.FULL -> Text("全额退款", color = MaterialTheme.colorScheme.tertiary)
            RefundStatus.NONE -> Unit
        }
        Text(expense.fullTimeText(zoneId), color = MaterialTheme.colorScheme.onSurfaceVariant)

        DetailSection("位置") {
            Text(expense.locationLabel.ifBlank { "位置已记录" })
        }
        if (expense.note.isNotBlank()) {
            DetailSection("备注") { Text(expense.note) }
        }
        if (expense.photos.isNotEmpty()) {
            DetailSection("照片") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(expense.photos, key = { _, photo -> photo.id }) { index, photo ->
                        Surface(onClick = { photoViewerIndex = index }) {
                            PhotoThumbnail(photo.path, Modifier.size(82.dp))
                        }
                    }
                }
            }
        }

        DetailSection("退款") {
            Row(Modifier.fillMaxWidth()) {
                Text("原消费")
                Spacer(Modifier.weight(1f))
                Text("¥${formatCents(expense.amountCents)}")
            }
            Row(Modifier.fillMaxWidth()) {
                Text("累计退款")
                Spacer(Modifier.weight(1f))
                Text("¥${formatCents(expense.refundedAmountCents)}")
            }
            Row(Modifier.fillMaxWidth()) {
                Text("实际支出", fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("¥${formatCents(expense.actualAmountCents)}", fontWeight = FontWeight.Bold)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onRefundRemaining,
                    enabled = expense.refundStatus != RefundStatus.FULL && !operationInFlight,
                    modifier = Modifier.weight(1f),
                ) { Text("全额退款") }
                Button(
                    onClick = onRecordRefund,
                    enabled = expense.refundStatus != RefundStatus.FULL && !operationInFlight,
                    modifier = Modifier.weight(1f),
                ) { Text("记录退款金额") }
            }
            if (expense.refunds.isNotEmpty()) {
                Text("退款记录", modifier = Modifier.padding(top = 14.dp, bottom = 4.dp), fontWeight = FontWeight.SemiBold)
                expense.refunds.sortedByDescending { it.occurredAt }.forEach { refund ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(refund.timeText(zoneId), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text("¥${formatCents(refund.amountCents)}")
                        TextButton(onClick = { pendingDeleteRefund = refund }, enabled = !operationInFlight) { Text("删除") }
                    }
                    HorizontalDivider()
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        TextButton(
            onClick = { confirmDeleteExpense = true },
            enabled = !operationInFlight,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("删除账单", color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(18.dp))
    }

    if (confirmDeleteExpense) {
        AlertDialog(
            onDismissRequest = { confirmDeleteExpense = false },
            title = { Text("删除这笔账单？") },
            text = { Text("关联的退款记录和照片也会永久删除。") },
            confirmButton = {
                TextButton(onClick = { confirmDeleteExpense = false; onDeleteExpense() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteExpense = false }) { Text("取消") } },
        )
    }
    pendingDeleteRefund?.let { refund ->
        AlertDialog(
            onDismissRequest = { pendingDeleteRefund = null },
            title = { Text("删除退款记录？") },
            text = { Text("实际支出和周/月统计会重新计算。") },
            confirmButton = {
                TextButton(onClick = { pendingDeleteRefund = null; onDeleteRefund(refund.id) }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteRefund = null }) { Text("取消") } },
        )
    }
    photoViewerIndex?.let { initialIndex ->
        var index by remember(initialIndex) { mutableIntStateOf(initialIndex) }
        Dialog(onDismissRequest = { photoViewerIndex = null }) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    ZoomablePhoto(
                        expense.photos[index].path,
                        Modifier.fillMaxWidth().height(480.dp),
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { if (index > 0) index-- }, enabled = index > 0) { Text("上一张") }
                        Spacer(Modifier.weight(1f))
                        Text("${index + 1} / ${expense.photos.size}")
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { if (index < expense.photos.lastIndex) index++ }, enabled = index < expense.photos.lastIndex) {
                            Text("下一张")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(5.dp))
        content()
    }
}

private fun Refund.timeText(zoneId: ZoneId): String =
    Instant.ofEpochMilli(occurredAt).atZone(zoneId).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
