package com.hackerli.jizhang.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import kotlin.math.absoluteValue

object TagPredictor {
    private const val THIRTY_DAYS = 30L * 24 * 60 * 60 * 1_000
    private const val NINETY_DAYS = 90L * 24 * 60 * 60 * 1_000
    private const val LOCATION_RADIUS_METERS = 200.0

    fun suggest(
        activeTags: List<QuickTag>,
        expenses: List<Expense>,
        location: LocationSnapshot,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<QuickTag> {
        if (activeTags.isEmpty()) return emptyList()
        val activeById = activeTags.associateBy { it.id }
        val recent = expenses.filter {
            it.tagId in activeById && it.occurredAt in (nowMillis - NINETY_DAYS)..nowMillis
        }
        val nearby = recent.filter {
            PricePredictor.distanceMeters(
                location.latitude,
                location.longitude,
                it.latitude,
                it.longitude,
            ) <= LOCATION_RADIUS_METERS
        }
        val exact = nearby.filter {
            sameDayType(it.occurredAt, nowMillis, zoneId) &&
                circularHourDifference(it.occurredAt, nowMillis, zoneId) <= 2
        }
        val result = linkedMapOf<Long, QuickTag>()
        listOf(exact, nearby, recent).forEach { candidates ->
            rank(activeTags, candidates, nowMillis).forEach { tag ->
                if (result.size < 3) result.putIfAbsent(tag.id, tag)
            }
        }
        activeTags.sortedBy { it.sortOrder }.forEach { tag ->
            if (result.size < 3) result.putIfAbsent(tag.id, tag)
        }
        return result.values.toList()
    }

    private fun rank(activeTags: List<QuickTag>, candidates: List<Expense>, nowMillis: Long): List<QuickTag> {
        if (candidates.isEmpty()) return emptyList()
        val stats = candidates.groupBy { it.tagId }.mapValues { (_, matches) ->
            TagScore(
                score = matches.sumOf { if (nowMillis - it.occurredAt <= THIRTY_DAYS) 3 else 1 },
                mostRecent = matches.maxOf { it.occurredAt },
            )
        }
        return activeTags.filter { it.id in stats }.sortedWith(
            compareByDescending<QuickTag> { stats.getValue(it.id).score }
                .thenByDescending { stats.getValue(it.id).mostRecent }
                .thenBy { it.sortOrder },
        )
    }

    private fun sameDayType(a: Long, b: Long, zoneId: ZoneId): Boolean =
        isWeekend(a, zoneId) == isWeekend(b, zoneId)

    private fun isWeekend(time: Long, zoneId: ZoneId): Boolean {
        val day = Instant.ofEpochMilli(time).atZone(zoneId).dayOfWeek
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
    }

    private fun circularHourDifference(a: Long, b: Long, zoneId: ZoneId): Int {
        val hourA = Instant.ofEpochMilli(a).atZone(zoneId).hour
        val hourB = Instant.ofEpochMilli(b).atZone(zoneId).hour
        val raw = (hourA - hourB).absoluteValue
        return minOf(raw, 24 - raw)
    }

    private data class TagScore(val score: Int, val mostRecent: Long)
}
