package com.sympauthy.api.mapper.admin

import com.sympauthy.api.resource.admin.AdminInteractiveFlowPurposeResource
import com.sympauthy.api.resource.admin.AdminInteractiveFlowSessionDebugInformationResource
import com.sympauthy.api.resource.admin.AdminInteractiveFlowSessionDetailResource
import com.sympauthy.api.resource.admin.AdminInteractiveFlowSessionPurposeProgressResource
import com.sympauthy.api.resource.admin.AdminInteractiveFlowSessionSecurityContextResource
import com.sympauthy.api.resource.admin.AdminInteractiveFlowSessionSummaryResource
import com.sympauthy.api.resource.admin.AdminUserResource
import com.sympauthy.business.manager.collection.InteractiveFlowSessionCollectionManager.InteractiveFlowSessionDetail
import com.sympauthy.business.manager.collection.InteractiveFlowSessionCollectionManager.InteractiveFlowSessionSummary
import com.sympauthy.business.manager.collection.InteractiveFlowSessionCollectionManager.InteractiveFlowSessionUser
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowPurposeProgress
import com.sympauthy.business.model.flow.InteractiveFlowSessionSecurityContext
import com.sympauthy.business.model.flow.PurposeDebugInformation
import com.sympauthy.server.ErrorMessages
import com.sympauthy.util.renderOrNull
import com.sympauthy.util.wireName
import io.micronaut.context.MessageSource
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.Locale

/**
 * Publishes the interactive flow sessions an operator reads.
 *
 * Written by hand rather than generated because it composes [AdminUserResourceMapper]: a session names its
 * person with the resource the user collection already publishes, so a console renders both with one component.
 */
@Singleton
class AdminInteractiveFlowSessionResourceMapper(
    @Inject private val userMapper: AdminUserResourceMapper,
    @Inject @param:ErrorMessages private val errorMessageSource: MessageSource
) {

    /**
     * Publish [summary] as one row of the collection.
     *
     * The observation reaches this resource as the address and the user agent of the place the session was
     * last driven from: an operator scanning a page is matching those two, and the rest of the trail — and
     * the place the edge put each entry — is on the detail.
     */
    fun toResource(summary: InteractiveFlowSessionSummary) = AdminInteractiveFlowSessionSummaryResource(
        id = summary.id,
        status = summary.status.wireName,
        initiatingPurpose = toPurposeResource(summary.initiatingPurpose),
        currentPurpose = summary.currentPurpose?.let(::toPurposeResource),
        clientId = summary.initiatingClientId,
        signedUp = summary.signedUp,
        user = summary.user?.let(::toUserResource),
        ip = summary.securityContext?.ip,
        userAgent = summary.securityContext?.userAgent,
        sessionDate = summary.sessionDate,
        expirationDate = summary.expirationDate
    )

    /**
     * Publish [detail] as the session's own page, reading what it failed with in [locale].
     *
     * **Both messages are published beside the keys that name them**, rather than instead of them: a key
     * may not be renamed without breaking a caller, a sentence may be reworded in any release, so a console
     * grouping sessions by how they failed reads the key and the person reading the page reads the sentence.
     *
     * **The technical message is published whatever `features.print-details-in-error` says.** That flag
     * keeps the server's internals away from a caller nobody vouched for; this surface is gated by
     * `admin:interactive-flow-sessions:read`, and the operator holding it is the reader that message is
     * written for, which is the one exemption the API standard names beside the flag's own rule.
     */
    fun toResource(
        detail: InteractiveFlowSessionDetail,
        locale: Locale
    ) = AdminInteractiveFlowSessionDetailResource(
        id = detail.id,
        status = detail.status.wireName,
        initiatingPurpose = toPurposeResource(detail.initiatingPurpose),
        clientId = detail.initiatingClientId,
        flowId = detail.flowId,
        user = detail.user?.let(::toUserResource),
        signedUp = detail.signedUp,
        sessionDate = detail.sessionDate,
        expirationDate = detail.expirationDate,
        errorDetailsId = detail.errorDetailsId,
        errorDetails = render(detail.errorDetailsId, detail.errorValues, locale),
        errorDescriptionId = detail.errorDescriptionId,
        errorDescription = render(detail.errorDescriptionId, detail.errorValues, locale),
        errorValues = detail.errorValues,
        purposes = detail.purposes.map(::toPurposeProgressResource)
    )

    /**
     * The message [messageId] names, read in [locale] with [values] interpolated into it, or null where the
     * failure named no such message and where this deployment holds none under it.
     *
     * **A key with no message yields no field rather than the key itself.** Unlike every other rendering
     * site, the key here comes out of a row rather than off a throw site: a session persisted before a code
     * was renamed holds the old one, and a rolling upgrade has both versions writing rows for as long as it
     * lasts. The identifier beside the absence still says what the session failed with.
     */
    private fun render(messageId: String?, values: Map<String, String>?, locale: Locale): String? = messageId
        ?.let { errorMessageSource.renderOrNull(it, locale, values.orEmpty()) }

    /**
     * Publish [purpose] as the value a caller branches on and the label a person reads it under.
     *
     * One shape wherever a purpose appears on this surface — the session's initiating purpose, the purpose
     * it is stopped at, and each entry of its purpose list — so that a console renders all three the same
     * way and nothing has to know which of them carries a label.
     */
    private fun toPurposeResource(purpose: InteractiveFlowPurpose) = AdminInteractiveFlowPurposeResource(
        value = purpose.wireName,
        displayName = purpose.displayName
    )

    private fun toUserResource(user: InteractiveFlowSessionUser): AdminUserResource =
        userMapper.toResource(user.user, user.identifierClaims)

    /**
     * Publish [securityContext] as one entry of a session's places.
     */
    fun toResource(
        securityContext: InteractiveFlowSessionSecurityContext
    ) = AdminInteractiveFlowSessionSecurityContextResource(
        ip = securityContext.ip,
        userAgent = securityContext.userAgent,
        countryCode = securityContext.countryCode,
        regionCode = securityContext.regionCode,
        region = securityContext.region,
        city = securityContext.city,
        timeZone = securityContext.timeZone,
        firstSeenDate = securityContext.firstSeenDate,
        lastSeenDate = securityContext.lastSeenDate,
        observationCount = securityContext.observationCount,
        provenDate = securityContext.provenDate
    )

    private fun toPurposeProgressResource(
        progress: InteractiveFlowPurposeProgress
    ) = AdminInteractiveFlowSessionPurposeProgressResource(
        purpose = toPurposeResource(progress.purpose),
        status = progress.status.wireName,
        debug = progress.debugInformation.map(::toDebugInformationResource)
    )

    private fun toDebugInformationResource(
        information: PurposeDebugInformation
    ) = AdminInteractiveFlowSessionDebugInformationResource(
        displayName = information.displayName,
        value = information.value
    )
}
