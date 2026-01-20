package com.trace.payments.infrastructure.adapter.web

import com.trace.payments.application.dto.request.CreatePaymentRequest
import com.trace.payments.application.dto.response.ErrorResponse
import com.trace.payments.application.dto.response.PaymentListMeta
import com.trace.payments.application.dto.response.PaymentListResponse
import com.trace.payments.application.dto.response.PolicyViolationResponse
import com.trace.payments.application.mapper.PaymentMapper
import com.trace.payments.application.service.CreatePaymentResult
import com.trace.payments.application.service.PaymentService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

fun Routing.paymentRoutes(service: PaymentService) {
    post("/wallets/{walletId}/payments") {
        val idempotencyKey = call.request.headers["Idempotency-Key"]?.takeIf { it.isNotBlank() }
            ?: run {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Idempotency-Key header is required"))
                return@post
            }
        val walletIdStr = call.parameters["walletId"] ?: run {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "walletId is required"))
            return@post
        }
        val walletId = try {
            UUID.fromString(walletIdStr)
        } catch (_: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Invalid walletId format"))
            return@post
        }
        val body = try {
            call.receive<CreatePaymentRequest>()
        } catch (_: Exception) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Invalid request body"))
            return@post
        }
        val amount = body.amount
            ?: run {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "amount is required"))
                return@post
            }
        val occurredAt = body.occurredAt
            ?: run {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "occurredAt is required"))
                return@post
            }
        if (amount <= BigDecimal.ZERO || amount > BigDecimal("1000")) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "amount must be between 0 and 1000"))
            return@post
        }
        when (val result = service.createPayment(walletId, amount, occurredAt, idempotencyKey)) {
            is CreatePaymentResult.Success -> {
                if (result.isNew) {
                    call.respond(HttpStatusCode.Created, PaymentMapper.toResponse(result.payment))
                } else {
                    call.respond(HttpStatusCode.OK, PaymentMapper.toResponse(result.payment))
                }
            }
            is CreatePaymentResult.Conflict -> {
                call.respond(HttpStatusCode.Conflict, ErrorResponse("DUPLICATE_REQUEST", "Same Idempotency-Key with different payload"))
            }
            is CreatePaymentResult.PolicyViolation -> {
                val e = result.e
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    PolicyViolationResponse(
                        message = e.message ?: "Policy limit exceeded",
                        policyType = e.policyType,
                        period = e.period?.name
                    )
                )
            }
            is CreatePaymentResult.WalletNotFound -> {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Wallet not found"))
            }
        }
    }

    get("/wallets/{walletId}/payments") {
        val walletIdStr = call.parameters["walletId"] ?: run {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "walletId is required"))
            return@get
        }
        val walletId = try {
            UUID.fromString(walletIdStr)
        } catch (_: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Invalid walletId format"))
            return@get
        }
        val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
        val cursor = call.request.queryParameters["cursor"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val startDate = call.request.queryParameters["startDate"]?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val endDate = call.request.queryParameters["endDate"]?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val result = service.listPayments(walletId, startDate, endDate, cursor, limit)
        if (result == null) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Wallet not found"))
            return@get
        }
        val data = result.data.map { PaymentMapper.toItemResponse(it) }
        val meta = PaymentListMeta(
            nextCursor = result.nextCursor,
            previousCursor = null,
            total = result.total,
            totalMatches = null
        )
        call.respond(HttpStatusCode.OK, PaymentListResponse(data, meta))
    }
}
