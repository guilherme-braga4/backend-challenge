package com.trace.payments.infrastructure.adapter.web

import com.trace.payments.application.dto.request.CreatePolicyRequest
import com.trace.payments.application.dto.response.ErrorResponse
import com.trace.payments.application.dto.response.PoliciesListMeta
import com.trace.payments.application.dto.response.PoliciesListResponse
import com.trace.payments.application.mapper.PolicyMapper
import com.trace.payments.application.service.PolicyService
import com.trace.payments.domain.model.PolicyCategory
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.UUID

fun Routing.policyRoutes(service: PolicyService) {
    post("/policies") {
        val body = call.receive<CreatePolicyRequest>()
        val name = body.name?.trim()?.takeIf { it.isNotBlank() }
            ?: run {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "name is required"))
                return@post
            }
        val category = when (body.category) {
            "VALUE_LIMIT" -> PolicyCategory.VALUE_LIMIT
            "TX_COUNT_LIMIT" -> PolicyCategory.TX_COUNT_LIMIT
            else -> {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "category must be VALUE_LIMIT or TX_COUNT_LIMIT"))
                return@post
            }
        }
        try {
            val policy = service.createPolicy(
                name = name,
                category = category,
                maxPerPayment = body.maxPerPayment,
                daytimeDailyLimit = body.daytimeDailyLimit,
                nighttimeDailyLimit = body.nighttimeDailyLimit,
                weekendDailyLimit = body.weekendDailyLimit,
                maxTxPerDay = body.maxTxPerDay
            )
            call.respond(HttpStatusCode.Created, PolicyMapper.toResponse(policy))
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", e.message ?: "Validation failed"))
        }
    }

    get("/policies") {
        val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
        val cursor = call.request.queryParameters["cursor"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val result = service.listPolicies(limit, cursor)
        val data = result.data.map { PolicyMapper.toResponse(it) }
        val meta = PoliciesListMeta(
            nextCursor = result.nextCursor,
            previousCursor = null,
            total = result.total,
            totalMatches = null
        )
        call.respond(HttpStatusCode.OK, PoliciesListResponse(data, meta))
    }
}
