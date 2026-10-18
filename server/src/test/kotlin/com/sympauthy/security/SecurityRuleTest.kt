package com.sympauthy.security

import com.sympauthy.security.SecurityRule.ADMIN_USERS_READ
import com.sympauthy.security.SecurityRule.CLIENT_USERS_READ
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.SecurityRule.IS_ADMIN
import com.sympauthy.security.SecurityRule.IS_CLIENT
import com.sympauthy.security.SecurityRule.IS_USER
import io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SecurityRuleTest {

    @Test
    fun `isGrantedByAccessToken - Grant the roles and the scopes an access token carries`() {
        listOf(IS_USER, IS_CLIENT, IS_ADMIN, ADMIN_USERS_READ, CLIENT_USERS_READ).forEach {
            assertTrue(SecurityRule.isGrantedByAccessToken(it), it)
        }
    }

    @Test
    fun `isGrantedByAccessToken - Grant neither the state nor anonymity`() {
        assertFalse(SecurityRule.isGrantedByAccessToken(HAS_STATE))
        assertFalse(SecurityRule.isGrantedByAccessToken(IS_ANONYMOUS))
    }
}
