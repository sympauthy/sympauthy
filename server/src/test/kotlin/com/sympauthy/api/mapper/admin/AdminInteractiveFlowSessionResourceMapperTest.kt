package com.sympauthy.api.mapper.admin

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.collection.InteractiveFlowSessionCollectionManager.InteractiveFlowSessionDetail
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSessionStatus
import com.sympauthy.config.model.EnabledFeaturesConfig
import com.sympauthy.exception.mapper.LocalizedErrorMapper
import com.sympauthy.util.DEFAULT_LOCALE
import io.micronaut.context.StaticMessageSource
import java.time.LocalDateTime
import java.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The messages are the test's own rather than the ones the server ships, because what is under test is
 * that the two keys a row carries are read at all — in the language asked for, with the values
 * interpolated — and a deployment rewording a sentence breaks nothing this asserts. That the codes have
 * messages to be read is the error bundle's own rule, held by [com.sympauthy.server.ErrorMessageBundleTest].
 */
class AdminInteractiveFlowSessionResourceMapperTest {

    private val detailsId = "auth.interactive_flow_session.validate.expired"
    private val descriptionId = "description.oauth2.expired"

    private val messageSource = StaticMessageSource()
        .addMessage(DEFAULT_LOCALE, detailsId, "The session passed its expiration date {expirationDate}.")
        .addMessage(DEFAULT_LOCALE, descriptionId, "That took too long. Please start again.")
        .addMessage(DEFAULT_LOCALE, "description.internal_server_error", "An unexpected error occurred.")
        .addMessage(Locale.FRANCE, detailsId, "La session a expiré le {expirationDate}.")

    /**
     * The flag is off, which is the shipped default and the case the technical message has to survive.
     */
    private val localizedErrorMapper = LocalizedErrorMapper(
        messageSource = messageSource,
        featuresConfig = EnabledFeaturesConfig(
            allowAccessToClientWithoutScope = false,
            emailValidation = false,
            grantUnhandledScopes = false,
            printDetailsInError = false
        )
    )

    private val mapper = AdminInteractiveFlowSessionResourceMapper(
        userMapper = AdminUserResourceMapper(),
        errorMessageSource = messageSource,
        localizedErrorMapper = localizedErrorMapper
    )

    private fun detail(
        status: InteractiveFlowSessionStatus,
        errorDetailsId: String? = null,
        errorDescriptionId: String? = null,
        errorValues: Map<String, String>? = null
    ) = InteractiveFlowSessionDetail(
        id = UUID.randomUUID(),
        status = status,
        initiatingPurpose = InteractiveFlowPurpose.OAUTH2_AUTHORIZE,
        initiatingClientId = "web-app",
        flowId = "flow-id",
        user = null,
        signedUp = false,
        sessionDate = LocalDateTime.of(2026, 8, 31, 9, 30),
        expirationDate = LocalDateTime.of(2026, 8, 31, 10, 0),
        errorDetailsId = errorDetailsId,
        errorDescriptionId = errorDescriptionId,
        errorValues = errorValues,
        purposes = emptyList()
    )

    private fun failedDetail(
        errorDetailsId: String? = this.detailsId,
        errorDescriptionId: String? = this.descriptionId,
        errorValues: Map<String, String>? = mapOf("expirationDate" to "2026-08-31T10:00")
    ) = detail(
        status = InteractiveFlowSessionStatus.FAILED,
        errorDetailsId = errorDetailsId,
        errorDescriptionId = errorDescriptionId,
        errorValues = errorValues
    )

    @Test
    fun `toResource - Read both messages a failure names, and keep the keys naming them`() {
        val resource = mapper.toResource(failedDetail(), DEFAULT_LOCALE)

        assertEquals(detailsId, resource.errorDetailsId)
        assertEquals(descriptionId, resource.errorDescriptionId)
        assertEquals("The session passed its expiration date 2026-08-31T10:00.", resource.errorDetails)
        assertEquals("That took too long. Please start again.", resource.errorDescription)
    }

    @Test
    fun `toResource - Read a message in the language asked for`() {
        val resource = mapper.toResource(failedDetail(), Locale.FRANCE)

        assertEquals("La session a expiré le 2026-08-31T10:00.", resource.errorDetails)
    }

    @Test
    fun `toResource - Publish the values raw as well as inside the sentences they were read into`() {
        val resource = mapper.toResource(failedDetail(), DEFAULT_LOCALE)

        assertEquals(mapOf("expirationDate" to "2026-08-31T10:00"), resource.errorValues)
    }

    @Test
    fun `toResource - Read the generic sentence where the failure names no message of its own`() {
        val resource = mapper.toResource(failedDetail(errorDescriptionId = null), DEFAULT_LOCALE)

        assertNull(resource.errorDescriptionId)
        assertEquals("An unexpected error occurred.", resource.errorDescription)
        assertEquals("The session passed its expiration date 2026-08-31T10:00.", resource.errorDetails)
    }

    @Test
    fun `toResource - Publish the technical message the flag would have hidden`() {
        val failure = failedDetail()
        // The same failure through the mapper the error page goes through, which is where the flag is
        // read: it answers no technical message at all, and this page answers one anyway.
        val onTheErrorPage = localizedErrorMapper.toLocalizedError(
            BusinessException(
                recoverable = false,
                detailsId = detailsId,
                descriptionId = descriptionId,
                values = failure.errorValues.orEmpty()
            ),
            DEFAULT_LOCALE
        )

        assertNull(onTheErrorPage.details)
        assertNotNull(mapper.toResource(failure, DEFAULT_LOCALE).errorDetails)
    }

    @Test
    fun `toResource - Leave both sentences out where this deployment holds no message under the keys`() {
        val resource = mapper.toResource(
            failedDetail(errorDetailsId = "auth.renamed_away", errorDescriptionId = "description.renamed_away"),
            DEFAULT_LOCALE
        )

        assertEquals("auth.renamed_away", resource.errorDetailsId)
        assertEquals("description.renamed_away", resource.errorDescriptionId)
        assertNull(resource.errorDetails)
        assertNull(resource.errorDescription)
    }

    @Test
    fun `toResource - Carry none of the five fields for a session that did not fail`() {
        val resource = mapper.toResource(detail(InteractiveFlowSessionStatus.ONGOING), DEFAULT_LOCALE)

        assertNull(resource.errorDetailsId)
        assertNull(resource.errorDetails)
        assertNull(resource.errorDescriptionId)
        assertNull(resource.errorDescription)
        assertNull(resource.errorValues)
    }
}
