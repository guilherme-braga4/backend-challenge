package com.trace.payments.application.mapper

import com.trace.payments.application.dto.response.WalletResponse
import com.trace.payments.domain.model.Wallet

object WalletMapper {

    fun toResponse(w: Wallet) = WalletResponse(
        id = w.id,
        ownerName = w.ownerName,
        createdAt = w.createdAt
    )
}
