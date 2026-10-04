package com.sympauthy.business.model.user.claim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RequestedClaimsTest {

    private val requested = RequestedClaims(
        idTokenClaimIds = setOf("loyalty_tier"),
        userInfoClaimIds = setOf("shoe_size")
    )

    @Test
    fun `namesIn - The id_token member for the id token`() {
        assertEquals(setOf("loyalty_tier"), requested.namesIn(ClaimPublicationPlace.ID_TOKEN))
    }

    @Test
    fun `namesIn - The userinfo member for userinfo`() {
        assertEquals(setOf("shoe_size"), requested.namesIn(ClaimPublicationPlace.USERINFO))
    }

    @Test
    fun `namesIn - Empty for every place no request can name`() {
        ClaimPublicationPlace.entries
            .filterNot(ClaimPublicationPlace::nameableInAClaimsRequest)
            .forEach { assertEquals(emptySet<String>(), requested.namesIn(it), "place $it") }
    }

    @Test
    fun `namesIn - Empty for every place when nothing was requested`() {
        ClaimPublicationPlace.entries.forEach {
            assertEquals(emptySet<String>(), RequestedClaims.NONE.namesIn(it), "place $it")
        }
    }

    @Test
    fun `Every nameable place is one that carries a value`() {
        ClaimPublicationPlace.entries
            .filter(ClaimPublicationPlace::nameableInAClaimsRequest)
            .forEach { assertTrue(it.carriesAValue, "place $it") }
    }
}
