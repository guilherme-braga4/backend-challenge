package com.trace.payments.application.dto.response

data class PoliciesResponse(
    val data: List<PolicyResponse>,
    val meta: PoliciesMeta
)

data class PoliciesMeta(val total: Int)
