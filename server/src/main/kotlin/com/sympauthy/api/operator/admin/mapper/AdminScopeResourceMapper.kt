package com.sympauthy.api.operator.admin.mapper

import com.sympauthy.api.operator.admin.resource.AdminScopeResource
import com.sympauthy.business.model.claim.Claim
import com.sympauthy.business.model.oauth2.*
import com.sympauthy.business.model.scope.Scope
import com.sympauthy.business.model.scope.ScopeType
import com.sympauthy.business.model.scope.isEnabled
import com.sympauthy.business.model.scope.origin
import com.sympauthy.platform.util.wireName
import jakarta.inject.Singleton

@Singleton
class AdminScopeResourceMapper {

    fun toResource(scope: Scope, claims: List<Claim>): AdminScopeResource {
        return AdminScopeResource(
            id = scope.scope,
            type = scope.type.wireName,
            origin = scope.origin.wireName,
            enabled = scope.isEnabled,
            claims = if (scope.type == ScopeType.CONSENTABLE) claims.map { it.id } else null
        )
    }
}
