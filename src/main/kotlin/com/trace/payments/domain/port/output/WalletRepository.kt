package com.trace.payments.domain.port.output

import com.trace.payments.domain.model.Wallet
import java.util.UUID

interface WalletRepository {
    fun getById(id: UUID): Wallet?
    fun lockAndGet(id: UUID): Wallet?
    fun insert(wallet: Wallet): Wallet
    fun updatePolicyIds(walletId: UUID, policyIds: List<UUID>)
}
