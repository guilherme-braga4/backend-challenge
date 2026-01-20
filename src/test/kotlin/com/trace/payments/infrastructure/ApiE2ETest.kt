package com.trace.payments.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.trace.payments.module
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID

@Ignore("Requer Docker. Remova @Ignore para rodar: ./gradlew test --tests 'com.trace.payments.infrastructure.ApiE2ETest'")
class ApiE2ETest {

    private val json = ObjectMapper()

    private fun idFromJson(body: String): String = json.readTree(body).get("id").asText()

    private lateinit var pg: PostgreSQLContainer<*>

    @BeforeTest
    fun before() {
        pg = PostgreSQLContainer(DockerImageName.parse("postgres:15"))
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test")
        pg.start()
        System.setProperty("test.database.jdbc.url", pg.jdbcUrl)
        System.setProperty("test.database.user", pg.username)
        System.setProperty("test.database.password", pg.password)
    }

    @AfterTest
    fun after() {
        if (::pg.isInitialized) pg.stop()
        System.clearProperty("test.database.jdbc.url")
        System.clearProperty("test.database.user")
        System.clearProperty("test.database.password")
    }

    @Test
    fun getHealth_returns200() = testApplication {
        application { module() }
        val r = client.get("/health")
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals("OK", r.bodyAsText())
    }

    @Test
    fun postWallets_returns201WithBody() = testApplication {
        application { module() }
        val r = client.post("/wallets") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerName":"E2E"}""")
        }
        assertEquals(HttpStatusCode.Created, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("id"))
        assertTrue(body.contains("ownerName"))
        assertTrue(body.contains("E2E"))
        assertTrue(body.contains("createdAt"))
    }

    @Test
    fun postPayments_returns201WithPayment() = testApplication {
        application { module() }
        val create = client.post("/wallets") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerName":"Pay"}""")
        }
        assertEquals(HttpStatusCode.Created, create.status)
        val walletBody = create.bodyAsText()
        val walletId = idFromJson(walletBody)
        val r = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "e2e-key-1")
            setBody("""{"amount":100,"occurredAt":"2024-08-26T10:00:00Z"}""")
        }
        assertEquals(HttpStatusCode.Created, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("paymentId"))
        assertTrue(body.contains("status"))
        assertTrue(body.contains("APPROVED"))
        assertTrue(body.contains("amount"))
        assertTrue(body.contains("occurredAt"))
    }

    @Test
    fun postPayments_exceedsLimit_returns422() = testApplication {
        application { module() }
        val create = client.post("/wallets") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerName":"Limit"}""")
        }
        val walletId = idFromJson(create.bodyAsText())
        val r = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "e2e-limit-1")
            setBody("""{"amount":5000,"occurredAt":"2024-08-26T10:00:00Z"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, r.status)
    }

    @Test
    fun postPayments_missingIdempotencyKey_returns400() = testApplication {
        application { module() }
        val create = client.post("/wallets") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerName":"NoKey"}""")
        }
        val walletId = idFromJson(create.bodyAsText())
        val r = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            setBody("""{"amount":100,"occurredAt":"2024-08-26T10:00:00Z"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test
    fun getPayments_returns200WithDataAndMeta() = testApplication {
        application { module() }
        val create = client.post("/wallets") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerName":"List"}""")
        }
        val walletId = idFromJson(create.bodyAsText())
        client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "e2e-list-1")
            setBody("""{"amount":50,"occurredAt":"2024-08-26T10:00:00Z"}""")
        }
        val r = client.get("/wallets/$walletId/payments")
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("data"))
        assertTrue(body.contains("meta"))
        assertTrue(body.contains("total"))
    }

    @Test
    fun getWalletPolicies_returns200WithDataAndMeta() = testApplication {
        application { module() }
        val create = client.post("/wallets") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerName":"Pol"}""")
        }
        val walletId = idFromJson(create.bodyAsText())
        val r = client.get("/wallets/$walletId/policies")
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("data"))
        assertTrue(body.contains("meta"))
        assertTrue(body.contains("total"))
    }

    @Test
    fun postPolicies_returns201() = testApplication {
        application { module() }
        val r = client.post("/policies") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"E2EPolicy","category":"VALUE_LIMIT","maxPerPayment":1000,"daytimeDailyLimit":4000,"nighttimeDailyLimit":1000,"weekendDailyLimit":1000}""")
        }
        assertEquals(HttpStatusCode.Created, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("id"))
        assertTrue(body.contains("name"))
        assertTrue(body.contains("E2EPolicy"))
    }

    @Test
    fun putWalletPolicy_returns204() = testApplication {
        application { module() }
        val w = client.post("/wallets") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerName":"PutPol"}""")
        }
        val policy = client.post("/policies") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"P2","category":"VALUE_LIMIT","maxPerPayment":1000,"daytimeDailyLimit":4000,"nighttimeDailyLimit":1000,"weekendDailyLimit":1000}""")
        }
        val walletId = idFromJson(w.bodyAsText())
        val policyId = idFromJson(policy.bodyAsText())
        val r = client.put("/wallets/$walletId/policy") {
            contentType(ContentType.Application.Json)
            setBody("""{"policyId":"$policyId"}""")
        }
        assertEquals(HttpStatusCode.NoContent, r.status)
    }

    @Test
    fun postPayments_txCountLimitActive_sixthReturns422() = testApplication {
        application { module() }
        val policy = client.post("/policies") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Tx5","category":"TX_COUNT_LIMIT","maxTxPerDay":5}""")
        }
        assertEquals(HttpStatusCode.Created, policy.status)
        val policyId = idFromJson(policy.bodyAsText())
        val w = client.post("/wallets") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerName":"TxCountE2E"}""")
        }
        val walletId = idFromJson(w.bodyAsText())
        val put = client.put("/wallets/$walletId/policy") {
            contentType(ContentType.Application.Json)
            setBody("""{"policyId":"$policyId"}""")
        }
        assertEquals(HttpStatusCode.NoContent, put.status)
        for (i in 0..4) {
            val r = client.post("/wallets/$walletId/payments") {
                contentType(ContentType.Application.Json)
                header("Idempotency-Key", "tx6-$i")
                setBody("""{"amount":10,"occurredAt":"2024-08-26T${10 + i}:00:00Z"}""")
            }
            assertEquals(HttpStatusCode.Created, r.status, "Payment ${i + 1} should be 201")
        }
        val sixth = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "tx6-5")
            setBody("""{"amount":10,"occurredAt":"2024-08-26T15:00:00Z"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, sixth.status)
        val body422 = sixth.bodyAsText()
        val tree = json.readTree(body422)
        assertTrue(tree.has("policyType"), "422 body should have policyType")
        assertEquals("TX_COUNT_LIMIT", tree.get("policyType").asText())
    }

    @Test
    fun getPolicies_returns200WithDataAndMeta() = testApplication {
        application { module() }
        val r = client.get("/policies")
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("data"))
        assertTrue(body.contains("meta"))
        assertTrue(body.contains("total"))
    }

    @Test
    fun postPayments_walletNotFound_returns404() = testApplication {
        application { module() }
        val walletId = UUID.randomUUID().toString()
        val r = client.post("/wallets/$walletId/payments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "404-post")
            setBody("""{"amount":100,"occurredAt":"2024-08-26T10:00:00Z"}""")
        }
        assertEquals(HttpStatusCode.NotFound, r.status)
        assertTrue(r.bodyAsText().contains("NOT_FOUND"))
    }

    @Test
    fun getPayments_walletNotFound_returns404() = testApplication {
        application { module() }
        val walletId = UUID.randomUUID().toString()
        val r = client.get("/wallets/$walletId/payments")
        assertEquals(HttpStatusCode.NotFound, r.status)
        assertTrue(r.bodyAsText().contains("NOT_FOUND"))
    }

    @Test
    fun getWalletPolicies_walletNotFound_returns404() = testApplication {
        application { module() }
        val walletId = UUID.randomUUID().toString()
        val r = client.get("/wallets/$walletId/policies")
        assertEquals(HttpStatusCode.NotFound, r.status)
        assertTrue(r.bodyAsText().contains("NOT_FOUND"))
    }
}
