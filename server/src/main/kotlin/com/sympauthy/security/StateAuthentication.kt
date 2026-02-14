package com.sympauthy.security

import com.sympauthy.security.SecurityRule.HAS_STATE
import io.micronaut.security.authentication.Authentication

class StateAuthentication(
    val state: String?
) : Authentication {
    override fun getName(): String = state ?: ""

    override fun getRoles(): Collection<String> {
        return listOf(HAS_STATE)
    }

    override fun getAttributes(): Map<String, Any> {
        return emptyMap()
    }
}

val Authentication.stateOrNull: String?
    get() = if (this is StateAuthentication) this.state else null
