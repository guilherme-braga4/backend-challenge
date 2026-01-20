package com.trace.payments.infrastructure.adapter.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object PaymentsTable : Table("payments") {
    val id = uuid("id")
    val walletId = uuid("wallet_id")
    val amount = decimal("amount", 19, 2)
    val occurredAt = timestamp("occurred_at")
    val idempotencyKey = varchar("idempotency_key", 255)
    val status = varchar("status", 20)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}
