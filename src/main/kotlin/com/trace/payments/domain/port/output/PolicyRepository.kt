package com.trace.payments.domain.port.output

import com.trace.payments.domain.model.Policy
import java.util.UUID

interface PolicyRepository {
    fun getById(id: UUID): Policy?
    fun insert(policy: Policy): Policy
    fun list(limit: Int, cursor: UUID?): Pair<List<Policy>, UUID?>
    fun count(): Int
}
