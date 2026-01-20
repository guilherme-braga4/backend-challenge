package com.trace.payments.domain.exception

import com.trace.payments.domain.model.Period

class PolicyViolationException(
    val policyType: String,
    val period: Period?,
    message: String
) : Exception(message)
