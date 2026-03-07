package com.sympauthy.api.mapper.admin

import com.sympauthy.api.mapper.config.OutputResourceMapperConfig
import com.sympauthy.api.resource.admin.AdminClientResource
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.Scope
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import java.net.URI

@Mapper(
    config = OutputResourceMapperConfig::class
)
abstract class AdminClientResourceMapper {

    @Mapping(source = "id", target = "clientId")
    abstract fun toResource(client: Client): AdminClientResource

    fun toScope(scope: Scope): String = scope.scope

    fun toUri(uri: URI): String = uri.toString()
}
