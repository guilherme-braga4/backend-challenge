package com.trace.payments.infrastructure.adapter.persistence

import com.trace.payments.domain.model.Policy
import com.trace.payments.domain.model.PolicyCategory
import com.trace.payments.domain.port.output.PolicyRepository
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SortOrder
import java.util.UUID

class ExposedPolicyRepository : PolicyRepository {

    override fun getById(id: UUID): Policy? {
        return PoliciesTable.select { PoliciesTable.id eq id }
            .singleOrNull()
            ?.toPolicy()
    }

    override fun insert(policy: Policy): Policy {
        PoliciesTable.insert {
            it[id] = policy.id
            it[name] = policy.name
            it[category] = policy.category.name
            it[maxPerPayment] = policy.maxPerPayment
            it[daytimeDailyLimit] = policy.daytimeDailyLimit
            it[nighttimeDailyLimit] = policy.nighttimeDailyLimit
            it[weekendDailyLimit] = policy.weekendDailyLimit
            it[maxTxPerDay] = policy.maxTxPerDay
            it[createdAt] = policy.createdAt
            it[updatedAt] = policy.updatedAt
        }
        return policy
    }

    override fun list(limit: Int, cursor: UUID?): Pair<List<Policy>, UUID?> {
        val query = if (cursor != null) {
            PoliciesTable.select { PoliciesTable.id greater cursor }
        } else {
            PoliciesTable.selectAll()
        }
        val rows = query.orderBy(PoliciesTable.id, SortOrder.ASC).limit(limit + 1).toList()
        val data = rows.take(limit).map { it.toPolicy() }
        val nextCursor = if (rows.size > limit) {
            rows[limit - 1][PoliciesTable.id]
        } else null
        return data to nextCursor
    }

    override fun count(): Int {
        return PoliciesTable.selectAll().count().toInt()
    }

    private fun org.jetbrains.exposed.sql.ResultRow.toPolicy() = Policy(
        id = this[PoliciesTable.id],
        name = this[PoliciesTable.name],
        category = PolicyCategory.valueOf(this[PoliciesTable.category]),
        maxPerPayment = this[PoliciesTable.maxPerPayment],
        daytimeDailyLimit = this[PoliciesTable.daytimeDailyLimit],
        nighttimeDailyLimit = this[PoliciesTable.nighttimeDailyLimit],
        weekendDailyLimit = this[PoliciesTable.weekendDailyLimit],
        maxTxPerDay = this[PoliciesTable.maxTxPerDay],
        createdAt = this[PoliciesTable.createdAt],
        updatedAt = this[PoliciesTable.updatedAt]
    )
}
