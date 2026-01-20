package com.trace.payments.application.dto.request

import java.math.BigDecimal

data class CreatePolicyRequest(
    val name: String? = null,
    val category: String? = null,
    val maxPerPayment: BigDecimal? = null,
    val daytimeDailyLimit: BigDecimal? = null,
    val nighttimeDailyLimit: BigDecimal? = null,
    val weekendDailyLimit: BigDecimal? = null,
    val maxTxPerDay: Int? = null
)
