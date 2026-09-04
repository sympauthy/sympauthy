package com.sympauthy.api.util

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.business.model.oauth2.ScopeType
import com.sympauthy.business.model.user.claim.ClaimOrigin
import io.micronaut.http.HttpStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FilterUtilTest {

    @Test
    fun `filterOf - Filter nothing when the parameter is absent`() {
        assertNull(filterOf<ScopeType>("type", null))
    }

    @Test
    fun `filterOf - Resolve the value published under the name`() {
        assertEquals(ScopeType.CONSENTABLE, filterOf<ScopeType>("type", "consentable"))
    }

    @Test
    fun `filterOf - Resolve the value whatever case it was sent in`() {
        assertEquals(ScopeType.CONSENTABLE, filterOf<ScopeType>("type", "ConSentable"))
    }

    @Test
    fun `filterOf - Refuse a value the set does not hold`() {
        val exception = assertThrows<LocalizedHttpException> {
            filterOf<ScopeType>("type", "consentible")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals("filter.value.unsupported", exception.detailsId)
        assertEquals("type", exception.values["parameter"])
        assertEquals("consentible", exception.values["value"])
        assertEquals("consentable, grantable, client", exception.values["supportedValues"])
    }

    @Test
    fun `filterOf - Refuse an empty value`() {
        val exception = assertThrows<LocalizedHttpException> {
            filterOf<ScopeType>("type", "")
        }

        assertEquals("filter.value.unsupported", exception.detailsId)
    }

    @Test
    fun `filterOf - Resolve against the name the caller says the value publishes`() {
        assertEquals(ClaimOrigin.OPENID_CONNECT, filterOf<ClaimOrigin>("origin", "openid") { it.value })

        val exception = assertThrows<LocalizedHttpException> {
            filterOf<ClaimOrigin>("origin", "openid_connect") { it.value }
        }

        assertEquals("openid, custom", exception.values["supportedValues"])
    }
}
