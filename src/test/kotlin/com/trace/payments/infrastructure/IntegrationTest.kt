package com.trace.payments.infrastructure

import com.trace.payments.application.service.CreatePaymentResult
import com.trace.payments.application.service.PaymentService
import com.trace.payments.application.service.PolicyService
import com.trace.payments.application.service.TxCountLimitValidator
import com.trace.payments.application.service.ValueLimitValidator
import com.trace.payments.application.service.WalletService
import com.trace.payments.domain.model.PolicyCategory
import com.trace.payments.infrastructure.adapter.persistence.ExposedPaymentRepository
import com.trace.payments.infrastructure.adapter.persistence.ExposedPolicyRepository
import com.trace.payments.infrastructure.adapter.persistence.ExposedWalletRepository
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Ignore("Requer Docker. Remova @Ignore para rodar: ./gradlew test --tests 'com.trace.payments.infrastructure.*'")
abstract class IntegrationTest {

    protected lateinit var pg: PostgreSQLContainer<*>
    protected lateinit var walletService: WalletService
    protected lateinit var policyService: PolicyService
    protected lateinit var paymentService: PaymentService

    @BeforeTest
    fun startDb() {
        pg = PostgreSQLContainer(DockerImageName.parse("postgres:15"))
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test")
        pg.start()
        val jdbcUrl = pg.jdbcUrl
        val ds = HikariDataSource(HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            username = pg.username
            password = pg.password
        })
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate()
        Database.connect(ds)
        val walletRepo = ExposedWalletRepository()
        val policyRepo = ExposedPolicyRepository()
        val paymentRepo = ExposedPaymentRepository()
        walletService = WalletService(walletRepo, policyRepo)
        policyService = PolicyService(policyRepo, walletRepo)
        val validators = listOf(
            ValueLimitValidator(policyRepo, paymentRepo),
            TxCountLimitValidator(policyRepo, paymentRepo)
        )
        paymentService = PaymentService(walletRepo, policyRepo, paymentRepo, validators)
    }

    @AfterTest
    fun stopDb() {
        if (::pg.isInitialized) pg.stop()
    }
}

@Ignore("Requer Docker. Remova @Ignore para rodar: ./gradlew test --tests 'com.trace.payments.infrastructure.*'")
class WalletAndPolicyIntegrationTest : IntegrationTest() {

    @Test
    fun createAndGetWallet() {
        val w = walletService.createWallet("Alice")
        assertNotNull(w.id)
        assertEquals("Alice", w.ownerName)
        val policies = walletService.getWalletPolicies(w.id)
        assertNotNull(policies)
        assertEquals(0, policies.size)
    }

    @Test
    fun createPolicyAndAssociateToWallet() {
        val policy = policyService.createPolicy(
            "P1", PolicyCategory.VALUE_LIMIT,
            BigDecimal("1000"), BigDecimal("4000"), BigDecimal("1000"), BigDecimal("1000"),
            null
        )
        val w = walletService.createWallet("Bob")
        policyService.setWalletPolicy(w.id, policy.id)
        val policies = walletService.getWalletPolicies(w.id)
        assertNotNull(policies)
        assertEquals(1, policies.size)
        assertEquals("P1", policies[0].name)
    }

    @Test
    fun getWalletPoliciesReturns404WhenWalletMissing() {
        val policies = walletService.getWalletPolicies(UUID.randomUUID())
        assertNull(policies)
    }
}

@Ignore("Requer Docker. Remova @Ignore para rodar: ./gradlew test --tests 'com.trace.payments.infrastructure.*'")
class PaymentIntegrationTest : IntegrationTest() {

    @Test
    fun createPayment() {
        val w = walletService.createWallet("Carol")
        val key = "key-${UUID.randomUUID()}"
        when (val r = paymentService.createPayment(w.id, BigDecimal("100"), Instant.parse("2024-08-26T10:00:00Z"), key)) {
            is CreatePaymentResult.Success -> {
                assertTrue(r.isNew)
                assertEquals(BigDecimal("100"), r.payment.amount)
            }
            else -> throw AssertionError("Expected Success, got $r")
        }
    }

    @Test
    fun idempotencySamePayloadReturns200() {
        val w = walletService.createWallet("Dave")
        val key = "key-${UUID.randomUUID()}"
        val amount = BigDecimal("50")
        val occurredAt = Instant.parse("2024-08-26T14:00:00Z")
        val r1 = paymentService.createPayment(w.id, amount, occurredAt, key)
        assertTrue(r1 is CreatePaymentResult.Success && r1.isNew)
        val r2 = paymentService.createPayment(w.id, amount, occurredAt, key)
        assertTrue(r2 is CreatePaymentResult.Success && !r2.isNew)
        assertEquals(r1.payment.id, r2.payment.id)
    }

    @Test
    fun idempotencyDifferentPayloadReturns409() {
        val w = walletService.createWallet("Eve")
        val key = "key-${UUID.randomUUID()}"
        paymentService.createPayment(w.id, BigDecimal("50"), Instant.parse("2024-08-26T14:00:00Z"), key)
        val r = paymentService.createPayment(w.id, BigDecimal("60"), Instant.parse("2024-08-26T14:00:00Z"), key)
        assertTrue(r is CreatePaymentResult.Conflict)
    }

    @Test
    fun policyViolationWhenExceedsLimit() {
        val w = walletService.createWallet("Frank")
        val key = "key-${UUID.randomUUID()}"
        val r = paymentService.createPayment(w.id, BigDecimal("5000"), Instant.parse("2024-08-26T10:00:00Z"), key)
        assertTrue(r is CreatePaymentResult.PolicyViolation)
    }

    @Test
    fun listPaymentsWithFilters() {
        val w = walletService.createWallet("Grace")
        paymentService.createPayment(w.id, BigDecimal("10"), Instant.parse("2024-08-25T10:00:00Z"), "k1")
        paymentService.createPayment(w.id, BigDecimal("20"), Instant.parse("2024-08-26T10:00:00Z"), "k2")
        paymentService.createPayment(w.id, BigDecimal("30"), Instant.parse("2024-08-27T10:00:00Z"), "k3")
        val all = paymentService.listPayments(w.id, null, null, null, 10)
        assertNotNull(all)
        assertEquals(3, all.data.size)
        val filtered = paymentService.listPayments(w.id, Instant.parse("2024-08-26T00:00:00Z"), Instant.parse("2024-08-26T23:59:59Z"), null, 10)
        assertNotNull(filtered)
        assertEquals(1, filtered.data.size)
        assertEquals(BigDecimal("20"), filtered.data[0].amount)
    }

    @Test
    fun resetDiario_valueLimitNewDayApproved() {
        val w = walletService.createWallet("ResetVal")
        paymentService.createPayment(w.id, BigDecimal("1000"), Instant.parse("2024-08-26T10:00:00Z"), "r1")
        paymentService.createPayment(w.id, BigDecimal("1000"), Instant.parse("2024-08-26T11:00:00Z"), "r2")
        paymentService.createPayment(w.id, BigDecimal("1000"), Instant.parse("2024-08-26T12:00:00Z"), "r3")
        paymentService.createPayment(w.id, BigDecimal("1000"), Instant.parse("2024-08-26T13:00:00Z"), "r4")
        val fifth = paymentService.createPayment(w.id, BigDecimal("500"), Instant.parse("2024-08-26T14:00:00Z"), "r5")
        assertTrue(fifth is CreatePaymentResult.PolicyViolation, "5th should exceed daytime 4000")
        val nextDay = paymentService.createPayment(w.id, BigDecimal("500"), Instant.parse("2024-08-27T10:00:00Z"), "r6")
        assertTrue(nextDay is CreatePaymentResult.Success, "500 on new day must be approved (limit reset)")
    }

    @Test
    fun txCountLimit_sixthRejected() {
        val policy = policyService.createPolicy(
            "Tx5", PolicyCategory.TX_COUNT_LIMIT,
            null, null, null, null, 5
        )
        val w = walletService.createWallet("Tx6")
        policyService.setWalletPolicy(w.id, policy.id)
        repeat(5) { i ->
            val r = paymentService.createPayment(w.id, BigDecimal("10"), Instant.parse("2024-08-26T10:00:00Z").plusSeconds(i * 3600L), "t6-$i")
            assertTrue(r is CreatePaymentResult.Success, "Payment ${i + 1} should succeed")
        }
        val sixth = paymentService.createPayment(w.id, BigDecimal("10"), Instant.parse("2024-08-26T16:00:00Z"), "t6-5")
        assertTrue(sixth is CreatePaymentResult.PolicyViolation, "6th must be rejected")
    }

    @Test
    fun txCountLimitBorderMidnight_excludesNextDayStart() {
        val policy = policyService.createPolicy(
            "Tx5", PolicyCategory.TX_COUNT_LIMIT,
            null, null, null, null, 5
        )
        val w = walletService.createWallet("TxBorder")
        policyService.setWalletPolicy(w.id, policy.id)
        repeat(5) { i ->
            val r = paymentService.createPayment(
                w.id, BigDecimal("10"),
                Instant.parse("2024-08-26T${10 + i}:00:00Z"),
                "txb-$i"
            )
            assertTrue(r is CreatePaymentResult.Success, "Payment ${i + 1} should succeed")
        }
        val sixthAtMidnightNextDay = paymentService.createPayment(
            w.id, BigDecimal("10"),
            Instant.parse("2024-08-27T00:00:00Z"),
            "txb-6"
        )
        assertTrue(sixthAtMidnightNextDay is CreatePaymentResult.Success, "6th at 00:00 D+1 must be in new day and approved")
    }

    @Test
    fun concurrencySumWithinLimit() {
        val w = walletService.createWallet("Hank")
        val n = 10
        val barrier = CyclicBarrier(n)
        val results = java.util.Collections.synchronizedList(mutableListOf<CreatePaymentResult>())
        val pool = Executors.newFixedThreadPool(n)
        repeat(n) {
            pool.submit {
                barrier.await()
                val key = "ck-${UUID.randomUUID()}"
                val r = paymentService.createPayment(w.id, BigDecimal("150"), Instant.parse("2024-08-26T20:00:00Z"), key)
                results.add(r)
            }
        }
        pool.shutdown()
        pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)
        val successes = results.filterIsInstance<CreatePaymentResult.Success>()
        val sum = successes.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.payment.amount) }
        assertTrue(sum <= BigDecimal("1000"), "Sum $sum should be <= 1000 (nighttime limit)")
    }
}
