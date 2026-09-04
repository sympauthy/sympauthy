package com.sympauthy.business.manager.mfa

import com.sympauthy.business.model.mfa.TotpEnrollment
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.page.orderedPage
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

/**
 * Manager responsible for reading the multi-factor authentication methods a user has enrolled.
 */
@Singleton
class MfaEnrollmentSearchManager(
    @Inject private val totpManager: TotpManager
) {

    /**
     * Read the page [pageParams] names of the enrollments the user [userId] has confirmed, oldest
     * confirmation first and ties broken by identifier.
     *
     * The order is the confirmation rather than the enrollment: an enrollment is listed once it is
     * confirmed, so confirming is what appends to the collection, and ordering on the enrollment
     * date would insert one started last week into a page a caller has already walked.
     */
    suspend fun listConfirmedEnrollments(
        userId: UUID,
        pageParams: PageParams
    ): Page<TotpEnrollment> {
        return totpManager.findConfirmedEnrollments(userId)
            .orderedPage(pageParams, compareBy<TotpEnrollment> { it.confirmedDate }.thenBy { it.id })
    }
}
