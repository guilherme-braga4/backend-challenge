package com.trace.payments.application.service

import com.trace.payments.domain.exception.PolicyViolationException
import com.trace.payments.domain.model.Period
import com.trace.payments.domain.model.PolicyCategory
import com.trace.payments.domain.model.Wallet
import com.trace.payments.domain.model.calculatePeriodRange
import com.trace.payments.domain.model.getPeriod
import com.trace.payments.domain.port.output.PaymentRepository
import com.trace.payments.domain.port.output.PolicyRepository
import com.trace.payments.domain.service.PolicyValidator
import java.math.BigDecimal
import java.time.Instant

class ValueLimitValidator(
    private val policyRepository: PolicyRepository,
    private val paymentRepository: PaymentRepository
) : PolicyValidator {

    override fun supports(category: PolicyCategory) = category == PolicyCategory.VALUE_LIMIT

    override fun validate(wallet: Wallet, amount: BigDecimal, occurredAt: Instant) {
        val effective = resolveEffectiveLimits(wallet)
        if (amount > effective.maxPerPayment) {
            throw PolicyViolationException("VALUE_LIMIT", null, "amount exceeds maxPerPayment (${effective.maxPerPayment})")
        }
        val period = getPeriod(occurredAt)
        val (start, end) = calculatePeriodRange(period, occurredAt)
        val sum = paymentRepository.sumByWalletAndPeriod(wallet.id, start, end)
        val limit = when (period) {
            Period.DAYTIME -> effective.daytimeDailyLimit
            Period.NIGHTTIME -> effective.nighttimeDailyLimit
            Period.WEEKEND -> effective.weekendDailyLimit
        }
        if (sum + amount > limit) {
            throw PolicyViolationException("VALUE_LIMIT", period, "Period limit exceeded for $period (limit=$limit, current=$sum, requested=$amount)")
        }
    }

    private fun resolveEffectiveLimits(wallet: Wallet): EffectiveValueLimits {
        val policy = wallet.policyIds
            .mapNotNull { policyRepository.getById(it) }
            .firstOrNull { it.category == PolicyCategory.VALUE_LIMIT }
        return if (policy != null && policy.category == PolicyCategory.VALUE_LIMIT) {
            EffectiveValueLimits(
                maxPerPayment = policy.maxPerPayment!!,
                daytimeDailyLimit = policy.daytimeDailyLimit!!,
                nighttimeDailyLimit = policy.nighttimeDailyLimit!!,
                weekendDailyLimit = policy.weekendDailyLimit!!
            )
        } else {
            EffectiveValueLimits(
                maxPerPayment = BigDecimal("1000"),
                daytimeDailyLimit = BigDecimal("4000"),
                nighttimeDailyLimit = BigDecimal("1000"),
                weekendDailyLimit = BigDecimal("1000")
            )
        }
    }

    private data class EffectiveValueLimits(
        val maxPerPayment: BigDecimal,
        val daytimeDailyLimit: BigDecimal,
        val nighttimeDailyLimit: BigDecimal,
        val weekendDailyLimit: BigDecimal
    )
}
