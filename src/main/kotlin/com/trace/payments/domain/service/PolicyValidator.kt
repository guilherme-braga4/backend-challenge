package com.trace.payments.domain.service

import com.trace.payments.domain.model.PolicyCategory
import com.trace.payments.domain.model.Wallet
import java.math.BigDecimal
import java.time.Instant

interface PolicyValidator {
    fun supports(category: PolicyCategory): Boolean
    fun validate(wallet: Wallet, amount: BigDecimal, occurredAt: Instant)
}
