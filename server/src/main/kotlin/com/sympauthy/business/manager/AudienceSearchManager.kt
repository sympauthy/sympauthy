package com.sympauthy.business.manager

import com.sympauthy.business.model.audience.Audience
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
            .map { it.withClientCount(clientCountsByAudienceId) }
    }

    /**
     * Read the audience identified by [audienceId], with the number of clients that belong to it.
     * Otherwise, return null if no audience matches.
     */
    suspend fun findAudienceByIdOrNull(audienceId: String): AudienceWithClientCount? {
        val audience = audienceManager.findAudienceByIdOrNull(audienceId) ?: return null
        return audience.withClientCount(clientManager.countClientsByAudienceId())
    }

    internal fun Audience.withClientCount(
        clientCountsByAudienceId: Map<String, Int>
    ) = AudienceWithClientCount(this, clientCountsByAudienceId[id] ?: 0)

    /**
     * An audience, and the number of clients that belong to it.
     *
     * How many clients an audience groups is what an administrator reading the configuration asks
     * about it, so a listing answers with both rather than leaving its caller to count them per
     * audience.
     */
    data class AudienceWithClientCount(
        val audience: Audience,
        val clientCount: Int
    )
}
