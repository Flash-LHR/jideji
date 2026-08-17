package com.hackerli.jizhang.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TagPredictorTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val location = LocationSnapshot(39.984, 116.307, 10f, "测试地点")
    private val tags = listOf(
        QuickTag(1, "麦当劳", "🍔", 0, 0),
        QuickTag(2, "地铁", "🚇", 0, 1),
        QuickTag(3, "打车", "🚕", 0, 2),
        QuickTag(4, "公交", "🚌", 0, 3),
    )

    @Test
    fun exactContextUsesWeightedFrequency() {
        val now = at(2026, 8, 17, 12)
        val expenses = listOf(
            expense(1, 1, now - days(7)),
            expense(2, 1, now - days(35)),
            expense(3, 2, now - days(14)),
        )

        assertEquals(listOf(1L, 2L, 3L), TagPredictor.suggest(tags, expenses, location, now, zone).map { it.id })
    }

    @Test
    fun noHistoryFallsBackToFixedOrder() {
        assertEquals(listOf(1L, 2L, 3L), TagPredictor.suggest(tags, emptyList(), location, at(2026, 8, 17, 12), zone).map { it.id })
    }

    private fun expense(id: Long, tagId: Long, time: Long) = Expense(
        id = id,
        amountCents = 2_000,
        tagId = tagId,
        tagName = "标签",
        tagEmoji = "●",
        tagColorArgb = 0,
        occurredAt = time,
        note = "",
        latitude = location.latitude,
        longitude = location.longitude,
        locationAccuracyMeters = 10f,
        locationLabel = "测试地点",
    )

    private fun at(year: Int, month: Int, day: Int, hour: Int): Long =
        LocalDateTime.of(year, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun days(value: Long) = value * 24 * 60 * 60 * 1_000
}
