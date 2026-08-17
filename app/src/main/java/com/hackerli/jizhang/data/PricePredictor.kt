package com.hackerli.jizhang.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object PricePredictor {
    private const val LOOKBACK_MILLIS = 90L * 24 * 60 * 60 * 1_000
    private const val LOCATION_RADIUS_METERS = 200.0

    fun suggestYuan(
        expenses: List<Expense>,
        tagId: Long,
        location: LocationSnapshot,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<Long> {
        val eligible = expenses.asSequence()
            .filter { it.tagId == tagId && it.refundStatus == RefundStatus.NONE }
            .filter { it.occurredAt in (nowMillis - LOOKBACK_MILLIS)..nowMillis }
            .toList()

        val nearby = eligible.filter {
            distanceMeters(location.latitude, location.longitude, it.latitude, it.longitude) <=
                LOCATION_RADIUS_METERS
        }
        return rankedAmounts(if (nearby.isNotEmpty()) nearby else eligible)
    }

    private fun rankedAmounts(expenses: List<Expense>): List<Long> = expenses
        .groupBy { it.amountCents / 100L }
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<Long, List<Expense>>> { it.value.size }
                .thenByDescending { entry -> entry.value.maxOf { it.occurredAt } }
                .thenBy { it.key },
        )
        .map { it.key }
        .filter { it > 0L }
        .take(3)

    internal fun distanceMeters(
        latitudeA: Double,
        longitudeA: Double,
        latitudeB: Double,
        longitudeB: Double,
    ): Double {
        val earthRadiusMeters = 6_371_000.0
        val latA = Math.toRadians(latitudeA)
        val latB = Math.toRadians(latitudeB)
        val deltaLat = Math.toRadians(latitudeB - latitudeA)
        val deltaLon = Math.toRadians(longitudeB - longitudeA)
        val haversine = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(latA) * cos(latB) * sin(deltaLon / 2) * sin(deltaLon / 2)
        return earthRadiusMeters * 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
    }
}
