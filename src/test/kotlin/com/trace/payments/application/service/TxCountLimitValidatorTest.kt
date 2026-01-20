package com.trace.payments.application.service

import com.trace.payments.domain.model.Payment
import com.trace.payments.domain.model.Policy
import com.trace.payments.domain.model.PolicyCategory
import com.trace.payments.domain.model.Wallet
import com.trace.payments.domain.port.output.PaymentRepository
import com.trace.payments.domain.port.output.PolicyRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith

class TxCountLimitValidatorTest {

    private val walletId = UUID.randomUUID()
    private val policyId = UUID.randomUUID()

    @Test
    fun throwsWhenCountExceedsMaxTxPerDay() {
        val policy = Policy(
            policyId, "tx", PolicyCategory.TX_COUNT_LIMIT,
            null, null, null, null, 5,
            Instant.now(), Instant.now()
        )
        val policyRepo = object : PolicyRepository {
            override fun getById(id: UUID) = if (id == policyId) policy else null
            override fun insert(policy: Policy) = policy
            override fun list(limit: Int, cursor: UUID?) = emptyList<Policy>() to null
            override fun count() = 0
        }
        val paymentRepo = object : PaymentRepository {
            override fun insert(payment: Payment) = payment
            override fun findByIdempotencyKey(key: String) = null
            override fun list(walletId: UUID, startDate: Instant?, endDate: Instant?, cursor: UUID?, limit: Int) = emptyList<Payment>() to null
            override fun count(walletId: UUID, startDate: Instant?, endDate: Instant?) = 0
            override fun sumByWalletAndPeriod(walletId: UUID, start: Instant, end: Instant) = BigDecimal.ZERO
            override fun countByWalletAndDay(walletId: UUID, dayStart: Instant, dayEnd: Instant) = 5L
        }
        val wallet = Wallet(walletId, "x", listOf(policyId), Instant.now())
        val validator = TxCountLimitValidator(policyRepo, paymentRepo)
        assertFailsWith<com.trace.payments.domain.exception.PolicyViolationException> {
            validator.validate(wallet, BigDecimal("10"), Instant.parse("2024-08-26T10:00:00Z"))
        }
    }

    @Test
    fun doesNotThrowWhenCountUnderMaxTxPerDay() {
        val policy = Policy(
            policyId, "tx", PolicyCategory.TX_COUNT_LIMIT,
            null, null, null, null, 5,
            Instant.now(), Instant.now()
        )
        val policyRepo = object : PolicyRepository {
            override fun getById(id: UUID) = if (id == policyId) policy else null
            override fun insert(policy: Policy) = policy
            override fun list(limit: Int, cursor: UUID?) = emptyList<Policy>() to null
            override fun count() = 0
        }
        val paymentRepo = object : PaymentRepository {
            override fun insert(payment: Payment) = payment
            override fun findByIdempotencyKey(key: String) = null
            override fun list(walletId: UUID, startDate: Instant?, endDate: Instant?, cursor: UUID?, limit: Int) = emptyList<Payment>() to null
            override fun count(walletId: UUID, startDate: Instant?, endDate: Instant?) = 0
            override fun sumByWalletAndPeriod(walletId: UUID, start: Instant, end: Instant) = BigDecimal.ZERO
            override fun countByWalletAndDay(walletId: UUID, dayStart: Instant, dayEnd: Instant) = 2L
        }
        val wallet = Wallet(walletId, "x", listOf(policyId), Instant.now())
        val validator = TxCountLimitValidator(policyRepo, paymentRepo)
        validator.validate(wallet, BigDecimal("10"), Instant.parse("2024-08-26T10:00:00Z"))
    }
}
