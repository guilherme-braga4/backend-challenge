package com.trace.payments.application.service

import com.trace.payments.domain.exception.PolicyViolationException
import com.trace.payments.domain.model.PolicyCategory
import com.trace.payments.domain.model.Wallet
import com.trace.payments.domain.port.output.PaymentRepository
import com.trace.payments.domain.port.output.PolicyRepository
import com.trace.payments.domain.service.PolicyValidator
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

class TxCountLimitValidator(
    private val policyRepository: PolicyRepository,
    private val paymentRepository: PaymentRepository
) : PolicyValidator {

    override fun supports(category: PolicyCategory) = category == PolicyCategory.TX_COUNT_LIMIT

    override fun validate(wallet: Wallet, amount: BigDecimal, occurredAt: Instant) {
        val policy = wallet.policyIds
            .mapNotNull { policyRepository.getById(it) }
            .firstOrNull { it.category == PolicyCategory.TX_COUNT_LIMIT }
            ?: return
        if (policy.category != PolicyCategory.TX_COUNT_LIMIT || policy.maxTxPerDay == null) return
        val maxTx = policy.maxTxPerDay
        val date = occurredAt.atZone(ZoneOffset.UTC).toLocalDate()
        val dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val count = paymentRepository.countByWalletAndDay(wallet.id, dayStart, dayEnd)
        if (count >= maxTx) {
            throw PolicyViolationException("TX_COUNT_LIMIT", null, "Max transactions per day exceeded (limit=$maxTx)")
        }
    }
}
