package com.hackerli.jizhang.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseTest {
    @Test
    fun multipleRefundsCalculateActualAmountAndStatus() {
        val partial = expense(listOf(Refund(1, 1, 2_000, 1), Refund(2, 1, 3_000, 2)))
        assertEquals(5_000L, partial.actualAmountCents)
        assertEquals(RefundStatus.PARTIAL, partial.refundStatus)

        val full = partial.copy(refunds = partial.refunds + Refund(3, 1, 5_000, 3))
        assertEquals(0L, full.actualAmountCents)
        assertEquals(RefundStatus.FULL, full.refundStatus)
    }

    private fun expense(refunds: List<Refund>) = Expense(
        id = 1,
        amountCents = 10_000,
        tagId = 1,
        tagName = "麦当劳",
        tagEmoji = "🍔",
        tagColorArgb = 0,
        occurredAt = 1,
        note = "",
        latitude = 0.0,
        longitude = 0.0,
        locationAccuracyMeters = 1f,
        locationLabel = "测试地点",
        refunds = refunds,
    )
}
