package com.sympauthy.api.resource.client

import com.sympauthy.api.resource.allowableValuesOf
import com.sympauthy.business.model.invitation.InvitationStatus
import com.sympauthy.util.wireName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Holds the sets the client resources publish to the enums their mappers publish them from.
 */
class ClientAllowableValuesTest {

    @Test
    fun `Every invitation status is published on the invitation`() {
        assertEquals(
            InvitationStatus.entries.map { it.wireName }.toSet(),
            allowableValuesOf(ClientInvitationResource::class, "status").toSet()
        )
    }
}
