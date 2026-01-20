package com.trace.payments.infrastructure.adapter.persistence

import com.trace.payments.domain.model.Payment
import com.trace.payments.domain.model.PaymentStatus
import com.trace.payments.domain.port.output.PaymentRepository
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.sum
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class ExposedPaymentRepository : PaymentRepository {

    override fun insert(payment: Payment): Payment {
        PaymentsTable.insert {
            it[id] = payment.id
            it[walletId] = payment.walletId
            it[amount] = payment.amount
            it[occurredAt] = payment.occurredAt
            it[idempotencyKey] = payment.idempotencyKey
            it[status] = payment.status.name
            it[createdAt] = payment.createdAt
            it[updatedAt] = payment.updatedAt
        }
        return payment
    }

    override fun findByIdempotencyKey(key: String): Payment? {
        return PaymentsTable.select { PaymentsTable.idempotencyKey eq key }
            .singleOrNull()
            ?.toPayment()
    }

    override fun list(
        walletId: UUID,
        startDate: Instant?,
        endDate: Instant?,
        cursor: UUID?,
        limit: Int
    ): Pair<List<Payment>, UUID?> {
        val rows = PaymentsTable.select {
            var op = PaymentsTable.walletId eq walletId
            if (startDate != null) op = op and (PaymentsTable.occurredAt greaterEq startDate)
            if (endDate != null) op = op and (PaymentsTable.occurredAt lessEq endDate)
            if (cursor != null) op = op and (PaymentsTable.id greater cursor)
            op
        }
            .orderBy(PaymentsTable.id, SortOrder.ASC)
            .limit(limit + 1)
            .toList()
        val data = rows.take(limit).map { it.toPayment() }
        val nextCursor = if (rows.size > limit) {
            rows[limit - 1][PaymentsTable.id]
        } else null
        return data to nextCursor
    }

    override fun count(walletId: UUID, startDate: Instant?, endDate: Instant?): Int {
        return PaymentsTable.select {
            var op = PaymentsTable.walletId eq walletId
            if (startDate != null) op = op and (PaymentsTable.occurredAt greaterEq startDate)
            if (endDate != null) op = op and (PaymentsTable.occurredAt lessEq endDate)
            op
        }.count().toInt()
    }

    override fun sumByWalletAndPeriod(walletId: UUID, start: Instant, end: Instant): BigDecimal {
        val sum = PaymentsTable.amount.sum()
        val row = PaymentsTable.slice(sum)
            .select {
                (PaymentsTable.walletId eq walletId) and
                    (PaymentsTable.occurredAt greaterEq start) and
                    (PaymentsTable.occurredAt less end)
            }
            .singleOrNull()
        return (row?.get(sum) ?: java.math.BigDecimal.ZERO) as BigDecimal
    }

    override fun countByWalletAndDay(walletId: UUID, dayStart: Instant, dayEnd: Instant): Long {
        return PaymentsTable.select {
            (PaymentsTable.walletId eq walletId) and
                (PaymentsTable.occurredAt greaterEq dayStart) and
                (PaymentsTable.occurredAt less dayEnd)
        }.count()
    }

    private fun org.jetbrains.exposed.sql.ResultRow.toPayment() = Payment(
        id = this[PaymentsTable.id],
        walletId = this[PaymentsTable.walletId],
        amount = this[PaymentsTable.amount],
        occurredAt = this[PaymentsTable.occurredAt],
        idempotencyKey = this[PaymentsTable.idempotencyKey],
        status = PaymentStatus.valueOf(this[PaymentsTable.status]),
        createdAt = this[PaymentsTable.createdAt],
        updatedAt = this[PaymentsTable.updatedAt]
    )
}
