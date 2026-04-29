package com.sympauthy.api.resource.client

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Paginated list of invitations created by this client.")
@Serdeable
data class ClientInvitationListResource(
    @get:Schema(description = "Array of invitation records.")
    val invitations: List<ClientInvitationResource>,
    @get:Schema(description = "Current page number.")
    val page: Int,
    @get:Schema(description = "Number of results per page.")
    val size: Int,
    @get:Schema(description = "Total number of invitations matching the filters.")
    val total: Int
)
