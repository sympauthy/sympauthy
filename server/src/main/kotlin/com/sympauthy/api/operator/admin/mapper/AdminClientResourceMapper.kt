package com.sympauthy.api.operator.admin.mapper

import com.sympauthy.api.operator.admin.resource.AdminAuthorizationWebhookResource
import com.sympauthy.api.operator.admin.resource.AdminClientReferenceResource
import com.sympauthy.api.operator.admin.resource.AdminClientResource
import com.sympauthy.api.operator.admin.resource.AdminClientSummaryResource
import com.sympauthy.business.model.client.AuthorizationWebhook
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.ClientReference
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.scope.Scope
import com.sympauthy.platform.util.wireName
import jakarta.inject.Singleton

@Singleton
class AdminClientResourceMapper {

    fun toSummaryResource(client: Client): AdminClientSummaryResource {
        return AdminClientSummaryResource(
            clientId = client.id,
            displayName = client.displayName,
            type = toClientType(client.public),
            audienceId = client.audience.id,
            allowedRedirectUris = client.allowedRedirectUris,
        )
    }

    fun toResource(client: Client): AdminClientResource {
        return AdminClientResource(
            clientId = client.id,
            displayName = client.displayName,
            type = toClientType(client.public),
            audienceId = client.audience.id,
            allowedGrantTypes = client.allowedGrantTypes.map(::toGrantType),
            authorizationFlowId = client.authorizationFlow?.id,
            allowedScopes = client.allowedScopes?.map(::toScope),
            defaultScopes = client.defaultScopes?.map(::toScope) ?: emptyList(),
            allowedRedirectUris = client.allowedRedirectUris,
            returnUri = client.returnUri,
            allowedReturnUris = client.allowedReturnUris,
            authorizationWebhook = toWebhookResource(client.authorizationWebhook),
        )
    }

    /**
     * Publish [client] as a record naming it is published with: its identifier, and the display name the
     * configuration gives it where it gives one.
     */
    fun toReferenceResource(client: ClientReference) = AdminClientReferenceResource(
        id = client.id,
        displayName = client.displayName
    )

    fun toClientType(public: Boolean): String = if (public) "public" else "confidential"

    fun toScope(scope: Scope): String = scope.scope

    fun toGrantType(grantType: GrantType): String = grantType.wireName

    fun toWebhookResource(webhook: AuthorizationWebhook?): AdminAuthorizationWebhookResource? {
        return webhook?.let {
            AdminAuthorizationWebhookResource(
                url = it.url.toString(),
                onFailure = it.onFailure.wireName,
            )
        }
    }
}
