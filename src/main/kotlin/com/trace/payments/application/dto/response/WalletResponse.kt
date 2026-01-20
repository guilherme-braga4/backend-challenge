package com.trace.payments.application.dto.response

import java.time.Instant
import java.util.UUID

data class WalletResponse(
    val id: UUID,
    val ownerName: String,
    val createdAt: Instant
)
