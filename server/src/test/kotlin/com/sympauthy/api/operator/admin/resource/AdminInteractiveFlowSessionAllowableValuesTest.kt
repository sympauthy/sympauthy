package com.sympauthy.api.operator.admin.resource

import com.sympauthy.api.resource.allowableValuesOf
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowPurpose.LINK_PROVIDER
import com.sympauthy.business.model.flow.InteractiveFlowPurpose.MFA_ENROLLMENT
import com.sympauthy.business.model.flow.InteractiveFlowPurpose.OAUTH2_AUTHORIZE
import com.sympauthy.business.model.flow.InteractiveFlowPurposeStatus
import com.sympauthy.business.model.flow.InteractiveFlowSessionStatus
import com.sympauthy.business.model.flow.PurposeDebugInformationType
import com.sympauthy.platform.util.wireName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Holds the sets the admin interactive-flow resources publish to the enums they are published from.
 */
class AdminInteractiveFlowSessionAllowableValuesTest {

    @Test
    fun `Every purpose is published where any purpose of the session is published`() {
        listOf(
            AdminInteractiveFlowSessionSummaryResource::class to "currentPurpose",
            AdminInteractiveFlowSessionPurposeProgressResource::class to "purpose"
        ).forEach { (resource, property) ->
            assertEquals(
                InteractiveFlowPurpose.entries.map { it.wireName },
                allowableValuesOf(resource, property),
                "${resource.simpleName}.$property"
            )
        }
    }

    @Test
    fun `Every initiating purpose is published where the initiating purpose is published`() {
        listOf(
            AdminInteractiveFlowSessionSummaryResource::class to "initiatingPurpose",
            AdminInteractiveFlowSessionDetailResource::class to "initiatingPurpose"
        ).forEach { (resource, property) ->
            assertEquals(
                listOf(OAUTH2_AUTHORIZE, MFA_ENROLLMENT, LINK_PROVIDER).map { it.wireName },
                allowableValuesOf(resource, property),
                "${resource.simpleName}.$property"
            )
        }
    }

    @Test
    fun `Every session status is published on the collection row`() {
        assertEquals(
            InteractiveFlowSessionStatus.entries.map { it.wireName },
            allowableValuesOf(AdminInteractiveFlowSessionSummaryResource::class, "status")
        )
    }

    @Test
    fun `Every session status is published on the detail`() {
        assertEquals(
            InteractiveFlowSessionStatus.entries.map { it.wireName },
            allowableValuesOf(AdminInteractiveFlowSessionDetailResource::class, "status")
        )
    }

    @Test
    fun `Every purpose status is published on a purpose entry`() {
        assertEquals(
            InteractiveFlowPurposeStatus.entries.map { it.wireName },
            allowableValuesOf(AdminInteractiveFlowSessionPurposeProgressResource::class, "status")
        )
    }

    @Test
    fun `Every debug information type is published on a debug entry`() {
        assertEquals(
            PurposeDebugInformationType.entries.map { it.wireName },
            allowableValuesOf(AdminInteractiveFlowSessionDebugInformationResource::class, "type")
        )
    }
}
