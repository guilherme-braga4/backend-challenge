package com.trace.payments.application.service

import com.trace.payments.domain.model.Policy
import com.trace.payments.domain.model.Wallet
import com.trace.payments.domain.port.output.PolicyRepository
import com.trace.payments.domain.port.output.WalletRepository
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class WalletService(
    private val walletRepository: WalletRepository,
    private val policyRepository: PolicyRepository
) {

    fun createWallet(ownerName: String): Wallet {
        val trimmed = ownerName.trim()
        require(trimmed.isNotBlank()) { "ownerName must not be empty" }
        return transaction {
            val wallet = Wallet(
                id = UUID.randomUUID(),
                ownerName = trimmed,
                policyIds = emptyList(),
                createdAt = Instant.now()
            )
            walletRepository.insert(wallet)
        }
    }

    fun getWalletPolicies(walletId: UUID): List<Policy>? = transaction {
        val wallet = walletRepository.getById(walletId) ?: return@transaction null
        wallet.policyIds.mapNotNull { policyRepository.getById(it) }
    }
}
