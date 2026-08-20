package com.hackerli.jizhang.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hackerli.jizhang.data.Expense

@Composable
internal fun RefundScreen(
    expense: Expense,
    operationInFlight: Boolean,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var input by remember(expense.id) { mutableStateOf("") }
    val amountYuan = MoneyInput.toYuan(input)
    val remainingCents = expense.amountCents - expense.refundedAmountCents
    val tooLarge = amountYuan * 100L > remainingCents
    BackHandler { if (!operationInFlight) onBack() }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, enabled = !operationInFlight, contentPadding = PaddingValues(0.dp)) { Text("‹ 返回") }
            Spacer(Modifier.weight(1f))
            Text("记录退款", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
        }
        Row(modifier = Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            TagIcon(
                expense.tagEmoji,
                expense.tagImagePath,
                expense.tagColorArgb,
                expense.tagName,
                Modifier.size(36.dp),
            )
            Spacer(Modifier.width(9.dp))
            Text(expense.tagName, style = MaterialTheme.typography.titleMedium)
        }
        Text("原消费  ¥${formatCents(expense.amountCents)}", modifier = Modifier.padding(top = 10.dp))
        Text("已退款  ¥${formatCents(expense.refundedAmountCents)}")
        Text(
            "¥${formatYuan(amountYuan)}",
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
        )
        if (tooLarge) Text("退款总额不能超过原消费金额", color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.weight(1f))
        AmountKeypad(
            input = input,
            confirmEnabled = amountYuan > 0L && !tooLarge,
            onInputChange = { input = it },
            onConfirm = { onConfirm(amountYuan) },
            enabled = !operationInFlight,
        )
    }
}
