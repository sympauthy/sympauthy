package com.sympauthy.api.util

import com.sympauthy.business.model.filter.ValueFilter
import com.sympauthy.business.model.oauth2.ScopeType
import com.sympauthy.business.model.user.claim.ClaimOrigin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ValueFilterUtilTest {

    @Test
    fun `valueFilterOf - Filter nothing when the parameter is absent`() {
        assertEquals(ValueFilter.Unfiltered<ScopeType>(), valueFilterOf<ScopeType>(null))
    }

    @Test
    fun `valueFilterOf - Resolve the value published under the name`() {
        assertEquals(ValueFilter.Matching(ScopeType.CONSENTABLE), valueFilterOf<ScopeType>("consentable"))
    }

    @Test
    fun `valueFilterOf - Resolve the value whatever case it was sent in`() {
        assertEquals(ValueFilter.Matching(ScopeType.CONSENTABLE), valueFilterOf<ScopeType>("ConSentable"))
    }

    @Test
    fun `valueFilterOf - Match nothing when the parameter names no value`() {
        assertEquals(ValueFilter.MatchesNothing<ScopeType>(), valueFilterOf<ScopeType>("consentible"))
    }

    @Test
    fun `valueFilterOf - Match nothing when the parameter is empty`() {
        assertEquals(ValueFilter.MatchesNothing<ScopeType>(), valueFilterOf<ScopeType>(""))
    }

    @Test
    fun `valueFilterOf - Resolve against the name the caller says the value publishes`() {
        assertEquals(
            ValueFilter.Matching(ClaimOrigin.OPENID_CONNECT),
            valueFilterOf<ClaimOrigin>("openid") { it.value }
        )
        assertEquals(
            ValueFilter.MatchesNothing<ClaimOrigin>(),
            valueFilterOf<ClaimOrigin>("openid_connect") { it.value }
        )
    }
}
