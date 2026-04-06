package com.sympauthy.api.mapper.admin

import com.sympauthy.api.mapper.config.OutputResourceMapperConfig
import com.sympauthy.api.resource.admin.AdminClientResource
import com.sympauthy.business.model.client.Client
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
    abstract fun toResource(client: Client): AdminClientResource

    @org.mapstruct.Named("toClientType")
    fun toClientType(public: Boolean): String = if (public) "public" else "confidential"

    fun toScope(scope: Scope): String = scope.scope
}
