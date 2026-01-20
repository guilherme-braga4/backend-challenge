package com.trace.payments.application.service

import com.trace.payments.domain.exception.PolicyViolationException
import com.trace.payments.domain.model.Payment
import com.trace.payments.domain.model.PaymentStatus
import com.trace.payments.domain.model.PolicyCategory
import com.trace.payments.domain.model.Wallet
import com.trace.payments.domain.port.output.PaymentRepository
import com.trace.payments.domain.port.output.PolicyRepository
import com.trace.payments.domain.port.output.WalletRepository
import com.trace.payments.domain.service.PolicyValidator
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

sealed class CreatePaymentResult {
    data class Success(val payment: Payment, val isNew: Boolean) : CreatePaymentResult()
    object Conflict : CreatePaymentResult()
    data class PolicyViolation(val e: PolicyViolationException) : CreatePaymentResult()
    object WalletNotFound : CreatePaymentResult()
}

class PaymentService(
    private val walletRepository: WalletRepository,
    private val policyRepository: PolicyRepository,
    private val paymentRepository: PaymentRepository,
    private val validators: List<PolicyValidator>
) {

    fun createPayment(
        walletId: UUID,
        amount: BigDecimal,
        occurredAt: Instant,
        idempotencyKey: String
    ): CreatePaymentResult {
        return try {
            doCreatePayment(walletId, amount, occurredAt, idempotencyKey)
        } catch (e: PolicyViolationException) {
            CreatePaymentResult.PolicyViolation(e)
        } catch (e: Exception) {
            if (isUniqueConstraintViolation(e)) {
                handleIdempotencyConflict(idempotencyKey, walletId, amount, occurredAt)
            } else {
                throw e
            }
        }
    }

    private fun doCreatePayment(
        walletId: UUID,
        amount: BigDecimal,
        occurredAt: Instant,
        idempotencyKey: String
    ): CreatePaymentResult = transaction {
        val wallet = walletRepository.lockAndGet(walletId) ?: return@transaction CreatePaymentResult.WalletNotFound
        val existing = paymentRepository.findByIdempotencyKey(idempotencyKey)
        if (existing != null) {
            return@transaction if (existing.walletId == walletId && existing.amount.compareTo(amount) == 0 && existing.occurredAt == occurredAt) {
                CreatePaymentResult.Success(existing, false)
            } else {
                CreatePaymentResult.Conflict
            }
        }
        val activeCategories = resolveActivePolicyCategories(wallet.policyIds) { policyRepository.getById(it)?.category }
        validators.filter { v -> activeCategories.any { c -> v.supports(c) } }.forEach { it.validate(wallet, amount, occurredAt) }
        val now = Instant.now()
        val payment = Payment(
            id = UUID.randomUUID(),
            walletId = walletId,
            amount = amount,
            occurredAt = occurredAt,
            idempotencyKey = idempotencyKey,
            status = PaymentStatus.APPROVED,
            createdAt = now,
            updatedAt = now
        )
        paymentRepository.insert(payment)
        CreatePaymentResult.Success(payment, true)
    }

    private fun handleIdempotencyConflict(
        idempotencyKey: String,
        walletId: UUID,
        amount: BigDecimal,
        occurredAt: Instant
    ): CreatePaymentResult = transaction {
        val existing = paymentRepository.findByIdempotencyKey(idempotencyKey) ?: return@transaction CreatePaymentResult.Conflict
        if (existing.walletId == walletId && existing.amount.compareTo(amount) == 0 && existing.occurredAt == occurredAt) {
            CreatePaymentResult.Success(existing, false)
        } else {
            CreatePaymentResult.Conflict
        }
    }

    private fun isUniqueConstraintViolation(e: Exception): Boolean {
        var ex: Throwable? = e
        while (ex != null) {
            val msg = ex.message ?: ""
            if (ex.javaClass.name.contains("PSQLException") && msg.contains("23505")) return true
            if (msg.contains("uq_idempotency_key") || msg.contains("unique constraint")) return true
            ex = ex.cause
        }
        return false
    }

    data class ListPaymentsResult(val data: List<Payment>, val nextCursor: UUID?, val total: Int)

    fun listPayments(
        walletId: UUID,
        startDate: Instant?,
        endDate: Instant?,
        cursor: UUID?,
        limit: Int
    ): ListPaymentsResult? = transaction {
        walletRepository.getById(walletId) ?: return@transaction null
        val (data, nextCursor) = paymentRepository.list(walletId, startDate, endDate, cursor, limit)
        val total = paymentRepository.count(walletId, startDate, endDate)
        ListPaymentsResult(data, nextCursor, total)
    }
}

internal fun resolveActivePolicyCategories(
    policyIds: List<UUID>,
    getCategory: (UUID) -> PolicyCategory?
): Set<PolicyCategory> {
    val s = policyIds.mapNotNull { getCategory(it) }.toSet()
    return if (s.isEmpty()) setOf(PolicyCategory.VALUE_LIMIT) else s
}
