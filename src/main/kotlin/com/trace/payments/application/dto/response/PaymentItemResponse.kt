package com.trace.payments.application.dto.response

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PaymentItemResponse(
    val id: UUID,
    val walletId: UUID,
    val amount: BigDecimal,
    val occurredAt: Instant,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant
)
