package com.trace.payments.application.dto.request

import java.math.BigDecimal
import java.time.Instant

data class CreatePaymentRequest(
    val amount: BigDecimal? = null,
    val occurredAt: Instant? = null
)
