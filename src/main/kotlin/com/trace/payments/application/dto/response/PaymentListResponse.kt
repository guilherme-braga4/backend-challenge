package com.trace.payments.application.dto.response

import com.fasterxml.jackson.annotation.JsonInclude
import java.util.UUID

data class PaymentListResponse(
    val data: List<PaymentItemResponse>,
    val meta: PaymentListMeta
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PaymentListMeta(
    val nextCursor: UUID?,
    val previousCursor: UUID?,
    val total: Int,
    val totalMatches: Int? = null
)
