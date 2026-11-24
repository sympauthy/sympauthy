package com.sympauthy.api.operator.admin.mapper

import com.sympauthy.api.operator.admin.resource.AdminScopeResource
import com.sympauthy.business.claim.model.Claim
import com.sympauthy.business.scope.model.Scope
import com.sympauthy.business.scope.model.ScopeType
import com.sympauthy.business.scope.model.isEnabled
import com.sympauthy.business.scope.model.origin
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
