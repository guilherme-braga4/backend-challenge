package com.trace.payments.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class Payment(
    val id: UUID,
    val walletId: UUID,
    val amount: BigDecimal,
    val occurredAt: Instant,
    val idempotencyKey: String,
    val status: PaymentStatus,
    val createdAt: Instant,
    val updatedAt: Instant
)
