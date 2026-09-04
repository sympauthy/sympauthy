package com.sympauthy.business.manager.provider

import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.page.orderedPage
import com.sympauthy.business.model.provider.ProviderUserInfo
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

/**
 * Manager responsible for reading the third-party identity providers a user has linked.
 */
@Singleton
class UserProviderSearchManager(
    @Inject private val providerClaimsManager: ProviderClaimsManager
) {

    /**
     * Read the page [pageParams] names of the providers the user [userId] has linked, oldest link
     * first and ties broken by provider identifier.
     */
    suspend fun listUserProviders(
        userId: UUID,
        pageParams: PageParams
    ): Page<ProviderUserInfo> {
        return providerClaimsManager.findByUserId(userId)
            .orderedPage(pageParams, compareBy<ProviderUserInfo> { it.linkDate }.thenBy { it.providerId })
    }
}
