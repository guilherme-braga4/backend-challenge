package com.trace.payments.application.mapper

import com.trace.payments.application.dto.response.PolicyResponse
import com.trace.payments.domain.model.Policy

object PolicyMapper {

    fun toResponse(p: Policy) = PolicyResponse(
        id = p.id,
        name = p.name,
        category = p.category.name,
        maxPerPayment = p.maxPerPayment,
        daytimeDailyLimit = p.daytimeDailyLimit,
        nighttimeDailyLimit = p.nighttimeDailyLimit,
        weekendDailyLimit = p.weekendDailyLimit,
        maxTxPerDay = p.maxTxPerDay,
        createdAt = p.createdAt,
        updatedAt = p.updatedAt
    )
}
