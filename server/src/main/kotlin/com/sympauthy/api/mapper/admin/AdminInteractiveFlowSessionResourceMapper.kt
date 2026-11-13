package com.sympauthy.api.mapper.admin

import com.sympauthy.api.resource.admin.AdminInteractiveFlowSessionDebugInformationResource
import com.sympauthy.api.resource.admin.AdminInteractiveFlowSessionDetailResource
import com.sympauthy.api.resource.admin.AdminInteractiveFlowSessionPurposeProgressResource
import com.sympauthy.api.resource.admin.AdminInteractiveFlowSessionSecurityContextResource
import com.sympauthy.api.resource.admin.AdminInteractiveFlowSessionSummaryResource
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.collection.InteractiveFlowSessionCollectionManager.InteractiveFlowSessionDetail
import com.sympauthy.business.manager.collection.InteractiveFlowSessionCollectionManager.InteractiveFlowSessionSummary
import com.sympauthy.business.model.flow.InteractiveFlowPurposeProgress
import com.sympauthy.business.model.flow.InteractiveFlowSessionSecurityContext
import com.sympauthy.business.model.flow.PurposeDebugInformation
import com.sympauthy.exception.mapper.LocalizedErrorMapper
import com.sympauthy.util.wireName
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.Locale

/**
 * Publishes the interactive flow sessions an operator reads.
 *
 * Written by hand rather than generated because it composes [AdminUserResourceMapper] and
 * [AdminClientResourceMapper]: a session names its person and its client with the resources a grant names them
 * with, so a console renders each with one component.
 */
@Singleton
class AdminInteractiveFlowSessionResourceMapper(
    @Inject private val userMapper: AdminUserResourceMapper,
    @Inject private val clientMapper: AdminClientResourceMapper,
    @Inject private val localizedErrorMapper: LocalizedErrorMapper
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
        initiatingPurpose = summary.initiatingPurpose.wireName,
        currentPurpose = summary.currentPurpose?.wireName,
        client = summary.initiatingClient?.let(clientMapper::toReferenceResource),
        signedUp = summary.signedUp,
        user = summary.user?.let(userMapper::toResource),
        ip = summary.securityContext?.ip,
        userAgent = summary.securityContext?.userAgent,
        sessionDate = summary.sessionDate,
        expirationDate = summary.expirationDate
    )

    /**
     * Publish [detail] as the session's own page, reading what it failed with in [locale].
     *
     * **All four error fields come from the mapper the flow's error page goes through**, over the same
     * terminal failure, so the page reads what the person was told rather than a second rendering of it:
     * a failure naming no message of its own carries the generic sentence that was on the screen, and the
     * code beside it is the one that sentence was read under. Two renderings of one failure are two
     * sentences to reconcile the first time they disagree.
     *
     * **The technical message is asked for whatever `features.print-details-in-error` says**, which is
     * what that mapper's override is for: the flag keeps the server's internals away from a caller nobody
     * vouched for, and this surface is the one exemption the API standard names beside it.
     *
     * **Both messages are published beside the codes that name them**, rather than instead of them: a code
     * may not be renamed without breaking a caller, a sentence may be reworded in any release, so a console
     * grouping sessions by how they failed reads the code and the person reading the page reads the
     * sentence.
     *
     * **A code this deployment holds no message under yields no sentence rather than the code itself.** The
     * codes come out of a row rather than off a throw site: a session persisted before one was renamed
     * holds the old one, and a rolling upgrade has both versions writing rows for as long as it lasts. The
     * code beside the absence still says what the session failed with.
     */
    fun toResource(
        detail: InteractiveFlowSessionDetail,
        locale: Locale
    ): AdminInteractiveFlowSessionDetailResource {
        val failure = detail.errorDetailsId?.let { toLocalizedFailure(it, detail, locale) }
        return AdminInteractiveFlowSessionDetailResource(
            id = detail.id,
            status = detail.status.wireName,
            initiatingPurpose = detail.initiatingPurpose.wireName,
            client = detail.initiatingClient?.let(clientMapper::toReferenceResource),
            flowId = detail.flowId,
            user = detail.user?.let(userMapper::toResource),
            signedUp = detail.signedUp,
            sessionDate = detail.sessionDate,
            expirationDate = detail.expirationDate,
            errorDetailsId = failure?.errorCode,
            errorDetails = failure?.details,
            errorDescriptionId = failure?.descriptionId,
            errorDescription = failure?.description,
            errorValues = detail.errorValues,
            purposes = detail.purposes.map(::toPurposeProgressResource)
        )
    }

    /**
     * What the failure [detailsId] stopped the session with, rendered in [locale] for both its readers.
     *
     * The exception is rebuilt to reach the mapper the error page reaches, and as the terminal failure it
     * was: a session that failed is over, so the generic sentence answering for a failure naming none of
     * its own is the one that page showed.
     */
    private fun toLocalizedFailure(
        detailsId: String,
        detail: InteractiveFlowSessionDetail,
        locale: Locale
    ) = localizedErrorMapper.toLocalizedError(
        BusinessException(
            recoverable = false,
            detailsId = detailsId,
            descriptionId = detail.errorDescriptionId,
            values = detail.errorValues.orEmpty()
        ),
        locale,
        alwaysPrintDetails = true
    )

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
        purpose = progress.purpose.wireName,
        status = progress.status.wireName,
        debug = progress.debugInformation.map(::toDebugInformationResource)
    )

    private fun toDebugInformationResource(
        information: PurposeDebugInformation
    ) = AdminInteractiveFlowSessionDebugInformationResource(
        code = information.code,
        type = information.type.wireName,
        value = information.value
    )
}
