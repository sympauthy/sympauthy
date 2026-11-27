package com.sympauthy.business.flow.manager

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.flow.model.InteractiveFlowPurpose
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Every purpose reaching a handler the container published.
 *
 * The registry holds no list of handlers: it is given every bean implementing the interface, so a handler is
 * wired up by existing. [InteractiveFlowPurposeRegistryTest] proves the lookup over a list it assembles, which
 * is complete by construction. What it cannot see is a handler the container leaves out, or a purpose added
 * with none, so this holds the registry the container built rather than one a test assembled.
 */
@MicronautTest(environments = ["default", "test", "h2"], startApplication = false)
class InteractiveFlowPurposeWiringTest {

    @Inject
    lateinit var registry: InteractiveFlowPurposeRegistry

    @Test
    fun `Every flow purpose resolves to the handler declaring it`() {
        val unresolvedPurposes = InteractiveFlowPurpose.entries.filter { purpose ->
            try {
                registry.getForPurpose(purpose).purpose != purpose
            } catch (_: BusinessException) {
                true
            }
        }

        assertEquals(
            emptyList<InteractiveFlowPurpose>(), unresolvedPurposes,
            "These purposes resolve to no handler declaring them, so the first session carrying one fails " +
                "when it is advanced."
        )
    }
}
