package com.trace.payments.application.service

import com.trace.payments.domain.model.Policy
import com.trace.payments.domain.model.PolicyCategory
import com.trace.payments.domain.port.output.PolicyRepository
import com.trace.payments.domain.port.output.WalletRepository
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

data class PolicyListResult(val data: List<Policy>, val nextCursor: UUID?, val total: Int)

class PolicyService(
    private val policyRepository: PolicyRepository,
    private val walletRepository: WalletRepository
) {

    fun createPolicy(
        name: String,
        category: PolicyCategory,
        maxPerPayment: java.math.BigDecimal?,
        daytimeDailyLimit: java.math.BigDecimal?,
        nighttimeDailyLimit: java.math.BigDecimal?,
        weekendDailyLimit: java.math.BigDecimal?,
        maxTxPerDay: Int?
    ): Policy {
        when (category) {
            PolicyCategory.VALUE_LIMIT -> require(
                maxPerPayment != null && daytimeDailyLimit != null &&
                    nighttimeDailyLimit != null && weekendDailyLimit != null
            ) { "VALUE_LIMIT requires maxPerPayment, daytimeDailyLimit, nighttimeDailyLimit, weekendDailyLimit" }
            PolicyCategory.TX_COUNT_LIMIT -> require(maxTxPerDay != null) { "TX_COUNT_LIMIT requires maxTxPerDay" }
        }
        val now = Instant.now()
        val policy = Policy(
            id = UUID.randomUUID(),
            name = name,
            category = category,
            maxPerPayment = maxPerPayment,
            daytimeDailyLimit = daytimeDailyLimit,
            nighttimeDailyLimit = nighttimeDailyLimit,
            weekendDailyLimit = weekendDailyLimit,
            maxTxPerDay = maxTxPerDay,
            createdAt = now,
            updatedAt = now
        )
        return transaction { policyRepository.insert(policy) }
    }

    fun listPolicies(limit: Int, cursor: UUID?): PolicyListResult = transaction {
        val (data, nextCursor) = policyRepository.list(limit, cursor)
        val total = policyRepository.count()
        PolicyListResult(data, nextCursor, total)
    }

    fun setWalletPolicy(walletId: UUID, policyId: UUID): Boolean? = transaction {
        walletRepository.getById(walletId) ?: return@transaction null
        policyRepository.getById(policyId) ?: return@transaction null
        walletRepository.updatePolicyIds(walletId, listOf(policyId))
        true
    }
}
