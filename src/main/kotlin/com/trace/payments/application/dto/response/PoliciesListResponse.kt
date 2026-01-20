package com.trace.payments.application.dto.response

import com.fasterxml.jackson.annotation.JsonInclude
import java.util.UUID

data class PoliciesListResponse(
    val data: List<PolicyResponse>,
    val meta: PoliciesListMeta
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PoliciesListMeta(
    val nextCursor: UUID?,
    val previousCursor: UUID?,
    val total: Int,
    val totalMatches: Int? = null
)
