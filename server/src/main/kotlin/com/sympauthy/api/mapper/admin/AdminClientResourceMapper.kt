package com.sympauthy.api.mapper.admin

import com.sympauthy.api.mapper.config.OutputResourceMapperConfig
import com.sympauthy.api.resource.admin.AdminAuthorizationWebhookResource
import com.sympauthy.api.resource.admin.AdminClientResource
import com.sympauthy.api.resource.admin.AdminClientSummaryResource
import com.sympauthy.business.model.client.AuthorizationWebhook
import com.sympauthy.business.model.client.AuthorizationWebhookOnFailure
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.oauth2.Scope
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.NullValueMappingStrategy

@Mapper(
    config = OutputResourceMapperConfig::class,
    nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT
)
abstract class AdminClientResourceMapper {

    @Mapping(source = "id", target = "clientId")
    @Mapping(source = "public", target = "type", qualifiedByName = ["toClientType"])
    @Mapping(source = "audience.id", target = "audienceId")
    abstract fun toSummaryResource(client: Client): AdminClientSummaryResource

    @Mapping(source = "id", target = "clientId")
    @Mapping(source = "public", target = "type", qualifiedByName = ["toClientType"])
    @Mapping(source = "audience.id", target = "audienceId")
    @Mapping(source = "authorizationFlow.id", target = "authorizationFlowId")
    abstract fun toResource(client: Client): AdminClientResource

    @org.mapstruct.Named("toClientType")
    fun toClientType(public: Boolean): String = if (public) "public" else "confidential"

    fun toScope(scope: Scope): String = scope.scope

    fun toGrantType(grantType: GrantType): String = grantType.value

    fun toWebhookResource(webhook: AuthorizationWebhook?): AdminAuthorizationWebhookResource? {
        return webhook?.let {
            AdminAuthorizationWebhookResource(
                url = it.url.toString(),
                onFailure = toOnFailure(it.onFailure)
            )
        }
    }

    private fun toOnFailure(onFailure: AuthorizationWebhookOnFailure): String = when (onFailure) {
        AuthorizationWebhookOnFailure.DENY_ALL -> "deny_all"
        AuthorizationWebhookOnFailure.FALLBACK_TO_RULES -> "fallback_to_rules"
    }
}
