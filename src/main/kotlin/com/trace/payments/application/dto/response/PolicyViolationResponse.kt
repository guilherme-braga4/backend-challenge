package com.trace.payments.application.dto.response

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PolicyViolationResponse(
    val error: String = "POLICY_VIOLATION",
    val message: String,
    val policyType: String? = null,
    val period: String? = null
)
