package com.trace.payments.infrastructure.adapter.web

import com.trace.payments.application.dto.request.CreateWalletRequest
import com.trace.payments.application.dto.request.PutWalletPolicyRequest
import com.trace.payments.application.dto.response.ErrorResponse
import com.trace.payments.application.dto.response.PoliciesMeta
import com.trace.payments.application.dto.response.PoliciesResponse
import com.trace.payments.application.mapper.PolicyMapper
import com.trace.payments.application.mapper.WalletMapper
import com.trace.payments.application.service.PolicyService
import com.trace.payments.application.service.WalletService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.util.UUID

fun Routing.walletRoutes(walletService: WalletService, policyService: PolicyService) {
    post("/wallets") {
        val body = call.receive<CreateWalletRequest>()
        val ownerName = body.ownerName?.takeIf { it.isNotBlank() }
            ?: run {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "ownerName must not be empty"))
                return@post
            }
        try {
            val wallet = walletService.createWallet(ownerName)
            call.respond(HttpStatusCode.Created, WalletMapper.toResponse(wallet))
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", e.message ?: "Validation failed"))
        }
    }

    get("/wallets/{walletId}/policies") {
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
        val policies = walletService.getWalletPolicies(walletId)
        if (policies == null) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Wallet not found"))
            return@get
        }
        val data = policies.map { PolicyMapper.toResponse(it) }
        call.respond(HttpStatusCode.OK, PoliciesResponse(data, PoliciesMeta(data.size)))
    }

    put("/wallets/{walletId}/policy") {
        val walletIdStr = call.parameters["walletId"] ?: run {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "walletId is required"))
            return@put
        }
        val walletId = try {
            UUID.fromString(walletIdStr)
        } catch (_: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "Invalid walletId format"))
            return@put
        }
        val body = call.receive<PutWalletPolicyRequest>()
        val policyId = body.policyId ?: run {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "policyId is required"))
            return@put
        }
        when (policyService.setWalletPolicy(walletId, policyId)) {
            null -> call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Wallet or policy not found"))
            else -> call.respond(HttpStatusCode.NoContent)
        }
    }
}
