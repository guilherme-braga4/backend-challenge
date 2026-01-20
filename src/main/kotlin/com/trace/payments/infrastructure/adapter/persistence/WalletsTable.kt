package com.trace.payments.infrastructure.adapter.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object WalletsTable : Table("wallets") {
    val id = uuid("id")
    val ownerName = varchar("owner_name", 255)
    val policyId = uuid("policy_id").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

// Fazer o Desenho da Tabela de policies_config