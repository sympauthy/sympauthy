package com.sympauthy.api.mapper.admin

import com.sympauthy.api.resource.admin.AdminAuthorizationWebhookResource
import com.sympauthy.api.resource.admin.AdminClientResource
import com.sympauthy.api.resource.admin.AdminClientSummaryResource
import com.sympauthy.business.model.client.AuthorizationWebhook
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.util.wireName
import jakarta.inject.Singleton

@Singleton
class AdminClientResourceMapper {

    fun toSummaryResource(client: Client): AdminClientSummaryResource {
        return AdminClientSummaryResource(
            clientId = client.id,
            type = toClientType(client.public),
            audienceId = client.audience.id,
            allowedRedirectUris = client.allowedRedirectUris,
        )
    }

    fun toResource(client: Client): AdminClientResource {
        return AdminClientResource(
            clientId = client.id,
            type = toClientType(client.public),
            audienceId = client.audience.id,
            allowedGrantTypes = client.allowedGrantTypes.map(::toGrantType),
            authorizationFlowId = client.authorizationFlow?.id,
            allowedScopes = client.allowedScopes?.map(::toScope) ?: emptyList(),
            defaultScopes = client.defaultScopes?.map(::toScope) ?: emptyList(),
            allowedRedirectUris = client.allowedRedirectUris,
            authorizationWebhook = toWebhookResource(client.authorizationWebhook),
        )
    }

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
