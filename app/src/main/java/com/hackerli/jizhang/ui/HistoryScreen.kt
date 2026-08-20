package com.hackerli.jizhang.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hackerli.jizhang.data.Expense
import com.hackerli.jizhang.data.RefundStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private enum class HistoryPeriod { ALL, WEEK, MONTH }

@Composable
internal fun HistoryScreen(
    expenses: List<Expense>,
    modifier: Modifier = Modifier,
    onOpenExpense: (Long) -> Unit,
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zoneId)
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val currentMonth = YearMonth.from(today)
    var selectedPeriod by rememberSaveable { mutableStateOf(HistoryPeriod.ALL) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun exitSearch() {
        searching = false
        query = ""
        keyboardController?.hide()
    }

    BackHandler(enabled = searching, onBack = ::exitSearch)
    LaunchedEffect(searching) {
        if (searching) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val weekExpenses = expenses.filter { it.localDate(zoneId) in weekStart..today }
    val monthExpenses = expenses.filter { YearMonth.from(it.localDate(zoneId)) == currentMonth }
    val periodExpenses = if (searching) expenses else when (selectedPeriod) {
            HistoryPeriod.ALL -> expenses
            HistoryPeriod.WEEK -> weekExpenses
            HistoryPeriod.MONTH -> monthExpenses
        }
    val visible = remember(periodExpenses, query) {
        if (query.isBlank()) periodExpenses else periodExpenses.filter { it.matches(query.trim()) }
    }
    val dailyTotals = remember(visible, zoneId) {
        visible.groupBy { it.localDate(zoneId) }
            .mapValues { (_, dayExpenses) -> dayExpenses.sumOf { it.actualAmountCents } }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("账单", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                if (searching) {
                    exitSearch()
                } else {
                    selectedPeriod = HistoryPeriod.ALL
                    searching = true
                }
            }) {
                Text(if (searching) "取消" else "搜索")
            }
        }
        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                singleLine = true,
                placeholder = { Text("标签、备注、地点、金额或退款状态") },
            )
        }
        if (!searching) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SummaryCard(
                    title = "本周支出",
                    amountCents = weekExpenses.sumOf { it.actualAmountCents },
                    count = weekExpenses.size,
                    selected = selectedPeriod == HistoryPeriod.WEEK,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        selectedPeriod = if (selectedPeriod == HistoryPeriod.WEEK) HistoryPeriod.ALL else HistoryPeriod.WEEK
                    },
                )
                SummaryCard(
                    title = "本月支出",
                    amountCents = monthExpenses.sumOf { it.actualAmountCents },
                    count = monthExpenses.size,
                    selected = selectedPeriod == HistoryPeriod.MONTH,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        selectedPeriod = if (selectedPeriod == HistoryPeriod.MONTH) HistoryPeriod.ALL else HistoryPeriod.MONTH
                    },
                )
            }
        }

        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (query.isBlank()) "还没有账单" else "没有匹配的账单", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                itemsIndexed(visible, key = { _, expense -> expense.id }) { index, expense ->
                    val date = expense.localDate(zoneId)
                    val previousDate = visible.getOrNull(index - 1)?.localDate(zoneId)
                    if (index == 0 || previousDate != date) {
                        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 5.dp)) {
                            Text(dateHeader(date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            Text("¥${formatCents(dailyTotals.getValue(date))}", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    ExpenseRow(expense, zoneId) { onOpenExpense(expense.id) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    amountCents: Long,
    count: Int,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("¥${formatCents(amountCents)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("$count 笔", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, zoneId: ZoneId, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (expense.photos.isNotEmpty()) {
            PhotoThumbnail(expense.photos.first().path, Modifier.size(52.dp))
        } else {
            TagIcon(
                expense.tagEmoji,
                expense.tagImagePath,
                expense.tagColorArgb,
                expense.tagName,
                Modifier.size(52.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(expense.tagName, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(7.dp))
                Text(expense.timeText(zoneId), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                expense.locationLabel.ifBlank { "位置已记录" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = buildList {
                if (expense.note.isNotBlank()) add("备注")
                when (expense.refundStatus) {
                    RefundStatus.PARTIAL -> add("部分退款")
                    RefundStatus.FULL -> add("全额退款")
                    RefundStatus.NONE -> Unit
                }
            }.joinToString(" · ")
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("¥${formatCents(expense.actualAmountCents)}", fontWeight = FontWeight.Bold)
    }
}

internal fun Expense.localDate(zoneId: ZoneId): LocalDate =
    Instant.ofEpochMilli(occurredAt).atZone(zoneId).toLocalDate()

internal fun Expense.timeText(zoneId: ZoneId): String =
    Instant.ofEpochMilli(occurredAt).atZone(zoneId).format(DateTimeFormatter.ofPattern("HH:mm"))

internal fun Expense.fullTimeText(zoneId: ZoneId): String =
    Instant.ofEpochMilli(occurredAt).atZone(zoneId).format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))

private fun dateHeader(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))
    }
}

private fun Expense.matches(query: String): Boolean {
    val normalized = query.lowercase()
    val refundText = when (refundStatus) {
        RefundStatus.NONE -> ""
        RefundStatus.PARTIAL -> "部分退款"
        RefundStatus.FULL -> "全额退款"
    }
    return tagName.lowercase().contains(normalized) ||
        note.lowercase().contains(normalized) ||
        locationLabel.lowercase().contains(normalized) ||
        refundText.contains(query) ||
        (amountCents / 100L).toString() == query ||
        (actualAmountCents / 100L).toString() == query
}
