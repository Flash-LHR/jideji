package com.hackerli.jizhang.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PricePredictorTest {
    private val location = LocationSnapshot(31.2304, 121.4737, 20f, "测试地点")

    @Test
    fun ranksNearbyAmountsByFrequencyThenRecency() {
        val expenses = listOf(
            expense(id = 1, yuan = 20, time = 1),
            expense(id = 2, yuan = 25, time = 2),
            expense(id = 3, yuan = 20, time = 3),
            expense(id = 4, yuan = 30, time = 4),
        )

        assertEquals(listOf(20L, 30L, 25L), PricePredictor.suggestYuan(expenses, 7, location, nowMillis = 10))
    }

    @Test
    fun fallsBackToSameTagAtAnyLocation() {
        val expenses = listOf(
            expense(id = 1, yuan = 20, time = 1, tagId = 8),
            expense(id = 2, yuan = 50, time = 2, latitude = 31.2504),
        )

        assertEquals(listOf(50L), PricePredictor.suggestYuan(expenses, 7, location, nowMillis = 10))
    }

    @Test
    fun excludesRefundedAndOlderThanNinetyDays() {
        val now = 100L * 24 * 60 * 60 * 1_000
        val expenses = listOf(
            expense(id = 1, yuan = 20, time = now - 1_000).copy(
                refunds = listOf(Refund(1, 1, 100, now)),
            ),
            expense(id = 2, yuan = 30, time = now - 91L * 24 * 60 * 60 * 1_000),
        )

        assertTrue(PricePredictor.suggestYuan(expenses, 7, location, now).isEmpty())
    }

    private fun expense(
        id: Long,
        yuan: Long,
        time: Long,
        tagId: Long = 7,
        latitude: Double = location.latitude,
    ) = Expense(
        id = id,
        amountCents = yuan * 100,
        tagId = tagId,
        tagName = "餐饮",
        tagEmoji = "🍜",
        tagColorArgb = 0,
        occurredAt = time,
        note = "",
        latitude = latitude,
        longitude = location.longitude,
        locationAccuracyMeters = 10f,
        locationLabel = "测试地点",
    )
}
