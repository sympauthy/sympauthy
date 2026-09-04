package com.sympauthy.business.manager.consent

import com.sympauthy.business.model.oauth2.Consent
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.page.orderedPage
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

/**
 * Manager responsible for reading the consents a user granted.
 */
@Singleton
class ConsentSearchManager(
    @Inject private val consentManager: ConsentManager
) {

    /**
     * Read the page [pageParams] names of the consents the user [userId] has granted and not
     * revoked, oldest first and ties broken by identifier.
     */
    suspend fun listUserConsents(
        userId: UUID,
        pageParams: PageParams
    ): Page<Consent> {
        return consentManager.findActiveConsentsByUser(userId)
            .orderedPage(pageParams, compareBy<Consent> { it.consentedAt }.thenBy { it.id })
    }
}
