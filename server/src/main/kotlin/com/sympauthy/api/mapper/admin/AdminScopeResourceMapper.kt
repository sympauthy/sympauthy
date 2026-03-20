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
            type = toTypeString(scope),
            origin = scope.origin.value,
            enabled = true,
            claims = if (scope is ConsentableUserScope) claims.map { it.id } else null
        )
    }

    private fun toTypeString(scope: Scope): String = when (scope) {
        is ConsentableUserScope -> "consentable"
        is GrantableUserScope -> "grantable"
        is ClientScope -> "client"
    }
}
