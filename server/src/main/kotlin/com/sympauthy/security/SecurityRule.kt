package com.sympauthy.security

import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.business.model.oauth2.BuiltInClientScopeId

object SecurityRule {

    const val HAS_STATE = "ROLE_STATE"

    const val IS_CLIENT = "ROLE_CLIENT"

    const val IS_USER = "ROLE_USER"

    const val IS_ADMIN = "ROLE_ADMIN"

    const val ADMIN_CONFIG_READ = "SCOPE_${AdminScopeId.CONFIG_READ}"
    const val ADMIN_USERS_READ = "SCOPE_${AdminScopeId.USERS_READ}"
    const val ADMIN_USERS_WRITE = "SCOPE_${AdminScopeId.USERS_WRITE}"
    const val ADMIN_USERS_DELETE = "SCOPE_${AdminScopeId.USERS_DELETE}"
    const val ADMIN_CONSENT_READ = "SCOPE_${AdminScopeId.CONSENT_READ}"
    const val ADMIN_CONSENT_WRITE = "SCOPE_${AdminScopeId.CONSENT_WRITE}"
    const val ADMIN_INVITATIONS_READ = "SCOPE_${AdminScopeId.INVITATIONS_READ}"
    const val ADMIN_INVITATIONS_WRITE = "SCOPE_${AdminScopeId.INVITATIONS_WRITE}"

    const val CLIENT_USERS_READ = "SCOPE_${BuiltInClientScopeId.USERS_READ}"
    const val CLIENT_USERS_CLAIMS_READ = "SCOPE_${BuiltInClientScopeId.USERS_CLAIMS_READ}"
    const val CLIENT_USERS_CLAIMS_WRITE = "SCOPE_${BuiltInClientScopeId.USERS_CLAIMS_WRITE}"
}
