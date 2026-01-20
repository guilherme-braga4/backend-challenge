package com.trace.payments.infrastructure.adapter.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object PoliciesTable : Table("policies") {
    val id = uuid("id")
    val name = varchar("name", 255)
    val category = varchar("category", 50)
    val maxPerPayment = decimal("max_per_payment", 19, 2).nullable()
    val daytimeDailyLimit = decimal("daytime_daily_limit", 19, 2).nullable()
    val nighttimeDailyLimit = decimal("nighttime_daily_limit", 19, 2).nullable()
    val weekendDailyLimit = decimal("weekend_daily_limit", 19, 2).nullable()
    val maxTxPerDay = integer("max_tx_per_day").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}
