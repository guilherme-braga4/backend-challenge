package com.trace.payments.domain.port.output

import com.trace.payments.domain.model.Payment
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

interface PaymentRepository {
    fun insert(payment: Payment): Payment
    fun findByIdempotencyKey(key: String): Payment?
    fun list(walletId: UUID, startDate: Instant?, endDate: Instant?, cursor: UUID?, limit: Int): Pair<List<Payment>, UUID?>
    fun count(walletId: UUID, startDate: Instant?, endDate: Instant?): Int
    fun sumByWalletAndPeriod(walletId: UUID, start: Instant, end: Instant): BigDecimal
    fun countByWalletAndDay(walletId: UUID, dayStart: Instant, dayEnd: Instant): Long
}
