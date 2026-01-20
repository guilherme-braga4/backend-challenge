package com.trace.payments.domain.model

import java.time.Instant
import java.util.UUID

data class Wallet(
    val id: UUID,
    val ownerName: String,
    val policyIds: List<UUID>,
    val createdAt: Instant
)
