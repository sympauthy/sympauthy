package com.sympauthy.api.util

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.business.model.oauth2.ScopeType
import com.sympauthy.business.model.page.SortOrder
import com.sympauthy.business.model.user.claim.ClaimOrigin
import io.micronaut.http.HttpStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class QueryValueUtilTest {

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
    fun `filterOf - Resolve against the name the value declares it publishes`() {
        assertEquals(ClaimOrigin.OPENID_CONNECT, filterOf<ClaimOrigin>("origin", "openid"))

        val exception = assertThrows<LocalizedHttpException> {
            filterOf<ClaimOrigin>("origin", "openid_connect")
        }

        assertEquals("openid, custom", exception.values["supportedValues"])
    }

    @Test
    fun `orderOf - Resolve no direction when the parameter is absent`() {
        assertNull(orderOf("order", null))
    }

    @Test
    fun `orderOf - Resolve each direction`() {
        assertEquals(SortOrder.ASC, orderOf("order", "asc"))
        assertEquals(SortOrder.DESC, orderOf("order", "desc"))
    }

    @Test
    fun `orderOf - Resolve the direction whatever case it was sent in`() {
        assertEquals(SortOrder.DESC, orderOf("order", "DESC"))
    }

    @Test
    fun `orderOf - Refuse a word naming neither direction`() {
        val exception = assertThrows<LocalizedHttpException> {
            orderOf("order", "ascending")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals("order.value.unsupported", exception.detailsId)
        assertEquals("order", exception.values["parameter"])
        assertEquals("ascending", exception.values["value"])
        assertEquals("asc, desc", exception.values["supportedValues"])
    }

    @Test
    fun `orderOf - Refuse an empty direction`() {
        val exception = assertThrows<LocalizedHttpException> {
            orderOf("order", "")
        }

        assertEquals("order.value.unsupported", exception.detailsId)
    }
}
