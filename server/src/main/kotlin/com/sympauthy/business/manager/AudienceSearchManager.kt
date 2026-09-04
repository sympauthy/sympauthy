package com.sympauthy.business.manager

import com.sympauthy.business.model.audience.AudienceWithClientCount
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.page.map
import com.sympauthy.business.model.page.orderedPage
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Manager responsible for reading the audiences this deployment configured, each with the number of
 * clients that belong to it.
 *
 * The audiences and the clients are both configuration held in memory, so counting is a grouping
 * rather than a query.
 */
@Singleton
class AudienceSearchManager(
    @Inject private val audienceManager: AudienceManager,
    @Inject private val clientManager: ClientManager
) {

    /**
     * Read the page [pageParams] names of every configured audience, ordered by identifier, each
     * with the number of clients that belong to it.
     */
    suspend fun listAudiences(pageParams: PageParams): Page<AudienceWithClientCount> {
        val clientCountsByAudienceId = clientManager.countClientsByAudienceId()
        return audienceManager.listAudiences()
            .orderedPage(pageParams, compareBy { it.id })
            .map { AudienceWithClientCount(it, clientCountsByAudienceId[it.id] ?: 0) }
    }
}
