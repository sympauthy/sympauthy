package com.sympauthy.api.mapper.admin

import com.sympauthy.api.resource.admin.AdminScopeResource
import com.sympauthy.business.model.oauth2.*
import com.sympauthy.business.model.user.claim.Claim
import jakarta.inject.Singleton

@Singleton
class AdminScopeResourceMapper {

    fun toResource(scope: Scope, claims: List<Claim>): AdminScopeResource {
        return AdminScopeResource(
            id = scope.scope,
            type = scope.type.value,
            origin = scope.origin.value,
            enabled = scope.isEnabled,
            claims = if (scope.type == ScopeType.CONSENTABLE) claims.map { it.id } else null
        )
    }
}
