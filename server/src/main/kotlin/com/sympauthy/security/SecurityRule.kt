package com.sympauthy.security

import com.sympauthy.business.model.oauth2.AdminScopeId

object SecurityRule {

    const val HAS_STATE = "ROLE_STATE"

    const val IS_CLIENT = "ROLE_CLIENT"

    const val IS_USER = "ROLE_USER"

    const val IS_ADMIN = "ROLE_ADMIN"

    const val ADMIN_CONFIG_READ = "SCOPE_${AdminScopeId.CONFIG_READ}"
    const val ADMIN_USERS_READ = "SCOPE_${AdminScopeId.USERS_READ}"
    const val ADMIN_USERS_WRITE = "SCOPE_${AdminScopeId.USERS_WRITE}"
    const val ADMIN_USERS_DELETE = "SCOPE_${AdminScopeId.USERS_DELETE}"
    const val ADMIN_ACCESS_READ = "SCOPE_${AdminScopeId.ACCESS_READ}"
    const val ADMIN_ACCESS_WRITE = "SCOPE_${AdminScopeId.ACCESS_WRITE}"
}
