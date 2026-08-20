package com.hackerli.jizhang.data

data class QuickTag(
    val id: Long,
    val name: String,
    val emoji: String,
    val colorArgb: Int,
    val sortOrder: Int,
    val isArchived: Boolean = false,
    val imagePath: String? = null,
)

data class ExpensePhoto(
    val id: Long,
    val expenseId: Long,
    val path: String,
    val sortOrder: Int,
)

data class Refund(
    val id: Long,
    val expenseId: Long,
    val amountCents: Long,
    val occurredAt: Long,
)

enum class RefundStatus {
    NONE,
    PARTIAL,
    FULL,
}

data class Expense(
    val id: Long,
    val amountCents: Long,
    val tagId: Long,
    val tagName: String,
    val tagEmoji: String,
    val tagColorArgb: Int,
    val occurredAt: Long,
    val note: String,
    val latitude: Double,
    val longitude: Double,
    val locationAccuracyMeters: Float,
    val locationLabel: String,
    val photos: List<ExpensePhoto> = emptyList(),
    val refunds: List<Refund> = emptyList(),
    val tagImagePath: String? = null,
) {
    val refundedAmountCents: Long get() = refunds.sumOf { it.amountCents }
    val actualAmountCents: Long get() = (amountCents - refundedAmountCents).coerceAtLeast(0L)
    val refundStatus: RefundStatus
        get() = when {
            refundedAmountCents <= 0L -> RefundStatus.NONE
            refundedAmountCents >= amountCents -> RefundStatus.FULL
            else -> RefundStatus.PARTIAL
        }
}

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val label: String,
)

sealed interface LocationState {
    data object PermissionRequired : LocationState
    data object Loading : LocationState
    data class Ready(val location: LocationSnapshot) : LocationState
    data class Error(val message: String) : LocationState
}

object TagPalette {
    val colors = listOf(
        0xFF1F6F5F.toInt(),
        0xFFE07A5F.toInt(),
        0xFFE9C46A.toInt(),
        0xFF457B9D.toInt(),
        0xFF8D6E63.toInt(),
        0xFF6C757D.toInt(),
        0xFFB56576.toInt(),
        0xFF4D908E.toInt(),
    )
}
