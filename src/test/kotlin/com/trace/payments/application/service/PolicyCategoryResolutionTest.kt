package com.trace.payments.application.service

import com.trace.payments.domain.model.PolicyCategory
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolicyCategoryResolutionTest {

    @Test
    fun emptyPolicyIds_returnsValueLimitAsDefault() {
        val got = resolveActivePolicyCategories(emptyList()) { null }
        assertEquals(setOf(PolicyCategory.VALUE_LIMIT), got)
    }

    @Test
    fun policyIdsWithAllNullResolved_returnsValueLimitAsDefault() {
        val id = UUID.randomUUID()
        val got = resolveActivePolicyCategories(listOf(id)) { null }
        assertEquals(setOf(PolicyCategory.VALUE_LIMIT), got)
    }

    @Test
    fun singleValueLimit_returnsValueLimit() {
        val id = UUID.randomUUID()
        val got = resolveActivePolicyCategories(listOf(id)) { if (it == id) PolicyCategory.VALUE_LIMIT else null }
        assertEquals(setOf(PolicyCategory.VALUE_LIMIT), got)
    }

    @Test
    fun singleTxCountLimit_returnsTxCountLimit() {
        val id = UUID.randomUUID()
        val got = resolveActivePolicyCategories(listOf(id)) { if (it == id) PolicyCategory.TX_COUNT_LIMIT else null }
        assertEquals(setOf(PolicyCategory.TX_COUNT_LIMIT), got)
    }

    @Test
    fun twoPoliciesSameCategory_returnsOneCategory() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val get: (UUID) -> PolicyCategory? = { when (it) { a, b -> PolicyCategory.VALUE_LIMIT; else -> null } }
        val got = resolveActivePolicyCategories(listOf(a, b), get)
        assertEquals(1, got.size)
        assertTrue(PolicyCategory.VALUE_LIMIT in got)
    }

    @Test
    fun twoPoliciesDifferentCategories_returnsBoth() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val get: (UUID) -> PolicyCategory? = { when (it) { a -> PolicyCategory.VALUE_LIMIT; b -> PolicyCategory.TX_COUNT_LIMIT; else -> null } }
        val got = resolveActivePolicyCategories(listOf(a, b), get)
        assertEquals(2, got.size)
        assertTrue(PolicyCategory.VALUE_LIMIT in got)
        assertTrue(PolicyCategory.TX_COUNT_LIMIT in got)
    }
}
