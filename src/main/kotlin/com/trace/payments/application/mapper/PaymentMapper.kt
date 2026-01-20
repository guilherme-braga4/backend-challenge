package com.trace.payments.application.mapper

import com.trace.payments.application.dto.response.PaymentItemResponse
import com.trace.payments.application.dto.response.PaymentResponse
import com.trace.payments.domain.model.Payment

object PaymentMapper {

    fun toResponse(p: Payment) = PaymentResponse(
        paymentId = p.id,
        status = p.status.name,
        amount = p.amount,
        occurredAt = p.occurredAt
    )

    fun toItemResponse(p: Payment) = PaymentItemResponse(
        id = p.id,
        walletId = p.walletId,
        amount = p.amount,
        occurredAt = p.occurredAt,
        status = p.status.name,
        createdAt = p.createdAt,
        updatedAt = p.updatedAt
    )
}
