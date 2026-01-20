package com.trace.payments.application.dto.response

import com.fasterxml.jackson.annotation.JsonInclude
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PolicyResponse(
    val id: UUID,
    val name: String,
    val category: String,
    val maxPerPayment: BigDecimal? = null,
    val daytimeDailyLimit: BigDecimal? = null,
    val nighttimeDailyLimit: BigDecimal? = null,
    val weekendDailyLimit: BigDecimal? = null,
    val maxTxPerDay: Int? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)
