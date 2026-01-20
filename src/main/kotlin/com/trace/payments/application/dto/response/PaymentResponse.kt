package com.trace.payments.application.dto.response

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PaymentResponse(
    val paymentId: UUID,
    val status: String,
    val amount: BigDecimal,
    val occurredAt: Instant
)
