package com.sympauthy.platform.util

import com.sympauthy.business.model.claim.ClaimOrigin
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.scope.ScopeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnumUtilTest {

    @Test
    fun `wireName - Lowercase the name of a value of an enum declaring nothing`() {
        assertEquals("consentable", ScopeType.CONSENTABLE.wireName)
    }

    @Test
    fun `wireName - Lowercase the name of a value declaring nothing beside one that declares`() {
        assertEquals("custom", ClaimOrigin.CUSTOM.wireName)
        assertEquals("authorization_code", GrantType.AUTHORIZATION_CODE.wireName)
    }

    @Test
    fun `wireName - Read the name the value declares`() {
        assertEquals("openid", ClaimOrigin.OPENID_CONNECT.wireName)
        assertEquals("urn:ietf:params:oauth:grant-type:token-exchange", GrantType.TOKEN_EXCHANGE.wireName)
    }

    @Test
    fun `configName - Spell the name with dashes, whatever the value publishes`() {
        assertEquals("openid-connect", ClaimOrigin.OPENID_CONNECT.configName)
    }
}
