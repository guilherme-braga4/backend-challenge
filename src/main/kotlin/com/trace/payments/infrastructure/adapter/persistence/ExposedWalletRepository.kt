package com.trace.payments.infrastructure.adapter.persistence

import com.trace.payments.domain.model.Wallet
import com.trace.payments.domain.port.output.WalletRepository
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import java.util.UUID

class ExposedWalletRepository : WalletRepository {

    override fun getById(id: UUID): Wallet? {
        return WalletsTable.select { WalletsTable.id eq id }
            .singleOrNull()
            ?.toWallet()
    }

    override fun lockAndGet(id: UUID): Wallet? {
        return WalletsTable.select { WalletsTable.id eq id }
            .forUpdate()
            .singleOrNull()
            ?.toWallet()
    }

    override fun insert(wallet: Wallet): Wallet {
        WalletsTable.insert {
            it[id] = wallet.id
            it[ownerName] = wallet.ownerName
            it[policyId] = wallet.policyIds.firstOrNull()
            it[createdAt] = wallet.createdAt
        }
        return wallet
    }

    override fun updatePolicyIds(walletId: UUID, policyIds: List<UUID>) {
        WalletsTable.update({ WalletsTable.id eq walletId }) {
            it[WalletsTable.policyId] = policyIds.firstOrNull()
        }
    }

    private fun org.jetbrains.exposed.sql.ResultRow.toWallet() = Wallet(
        id = this[WalletsTable.id],
        ownerName = this[WalletsTable.ownerName],
        policyIds = this[WalletsTable.policyId]?.let { listOf(it) } ?: emptyList(),
        createdAt = this[WalletsTable.createdAt]
    )
}
