package com.trace.payments

import com.trace.payments.application.service.PaymentService
import com.trace.payments.application.service.PolicyService
import com.trace.payments.application.service.TxCountLimitValidator
import com.trace.payments.application.service.ValueLimitValidator
import com.trace.payments.application.service.WalletService
import com.trace.payments.infrastructure.adapter.persistence.ExposedPaymentRepository
import com.trace.payments.infrastructure.adapter.persistence.ExposedPolicyRepository
import com.trace.payments.infrastructure.adapter.persistence.ExposedWalletRepository
import com.trace.payments.infrastructure.adapter.web.paymentRoutes
import com.trace.payments.infrastructure.adapter.web.policyRoutes
import com.trace.payments.infrastructure.adapter.web.walletRoutes
import com.trace.payments.infrastructure.config.FrameworkConfig
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    embeddedServer(
        Netty,
        port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    ) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    FrameworkConfig.init(this)

    val walletRepo = ExposedWalletRepository()
    val policyRepo = ExposedPolicyRepository()
    val paymentRepo = ExposedPaymentRepository()
    val walletService = WalletService(walletRepo, policyRepo)
    val policyService = PolicyService(policyRepo, walletRepo)
    val validators = listOf(
        ValueLimitValidator(policyRepo, paymentRepo),
        TxCountLimitValidator(policyRepo, paymentRepo)
    )
    val paymentService = PaymentService(walletRepo, policyRepo, paymentRepo, validators)

    routing {
        walletRoutes(walletService, policyService)
        policyRoutes(policyService)
        paymentRoutes(paymentService)
        get("/health") { context.respondText("OK") }
    }
}
