package com.trace.payments.application.service

import com.trace.payments.domain.exception.PolicyViolationException
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ValueLimitValidatorTest {

    private val walletId = UUID.randomUUID()

    @Test
    fun throwsWhenAmountExceedsMaxPerPaymentWithDefaultLimits() {
        val policyRepo = object : PolicyRepository {
            override fun getById(id: UUID) = null
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
            override fun countByWalletAndDay(walletId: UUID, dayStart: Instant, dayEnd: Instant) = 0L
        }
        val wallet = Wallet(walletId, "x", emptyList(), Instant.now())
        val validator = ValueLimitValidator(policyRepo, paymentRepo)
        assertFailsWith<PolicyViolationException> {
            validator.validate(wallet, BigDecimal("1001"), Instant.parse("2024-08-26T10:00:00Z"))
        }
    }

    @Test
    fun throwsWhenPeriodSumPlusAmountExceedsDaytimeLimit() {
        val policyRepo = object : PolicyRepository {
            override fun getById(id: UUID) = null
            override fun insert(policy: Policy) = policy
            override fun list(limit: Int, cursor: UUID?) = emptyList<Policy>() to null
            override fun count() = 0
        }
        var sumResult = BigDecimal("3950")
        val paymentRepo = object : PaymentRepository {
            override fun insert(payment: Payment) = payment
            override fun findByIdempotencyKey(key: String) = null
            override fun list(walletId: UUID, startDate: Instant?, endDate: Instant?, cursor: UUID?, limit: Int) = emptyList<Payment>() to null
            override fun count(walletId: UUID, startDate: Instant?, endDate: Instant?) = 0
            override fun sumByWalletAndPeriod(walletId: UUID, start: Instant, end: Instant) = sumResult
            override fun countByWalletAndDay(walletId: UUID, dayStart: Instant, dayEnd: Instant) = 0L
        }
        val wallet = Wallet(walletId, "x", emptyList(), Instant.now())
        val validator = ValueLimitValidator(policyRepo, paymentRepo)
        val ex = assertFailsWith<PolicyViolationException> {
            validator.validate(wallet, BigDecimal("100"), Instant.parse("2024-08-26T10:00:00Z"))
        }
        assertEquals(com.trace.payments.domain.model.Period.DAYTIME, ex.period)
        assertEquals("VALUE_LIMIT", ex.policyType)
    }

    @Test
    fun doesNotThrowWhenWithinDefaultLimits() {
        val policyRepo = object : PolicyRepository {
            override fun getById(id: UUID) = null
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
            override fun countByWalletAndDay(walletId: UUID, dayStart: Instant, dayEnd: Instant) = 0L
        }
        val wallet = Wallet(walletId, "x", emptyList(), Instant.now())
        val validator = ValueLimitValidator(policyRepo, paymentRepo)
        validator.validate(wallet, BigDecimal("500"), Instant.parse("2024-08-26T10:00:00Z"))
    }
}
