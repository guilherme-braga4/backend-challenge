package com.trace.payments.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class Policy(
    val id: UUID,
    val name: String,
    val category: PolicyCategory,
    val maxPerPayment: BigDecimal?,
    val daytimeDailyLimit: BigDecimal?,
    val nighttimeDailyLimit: BigDecimal?,
    val weekendDailyLimit: BigDecimal?,
    val maxTxPerDay: Int?,
    val createdAt: Instant,
    val updatedAt: Instant
)
