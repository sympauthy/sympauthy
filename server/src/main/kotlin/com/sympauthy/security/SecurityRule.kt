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
    const val ADMIN_GRANTS_READ = "SCOPE_${AdminScopeId.GRANTS_READ}"
    const val ADMIN_GRANTS_WRITE = "SCOPE_${AdminScopeId.GRANTS_WRITE}"
    const val ADMIN_INTERACTIVE_FLOW_SESSIONS_READ = "SCOPE_${AdminScopeId.INTERACTIVE_FLOW_SESSIONS_READ}"

    const val CLIENT_USERS_READ = "SCOPE_${BuiltInClientScopeId.USERS_READ}"
    const val CLIENT_USERS_MFA_READ = "SCOPE_${BuiltInClientScopeId.USERS_MFA_READ}"
    const val CLIENT_USERS_MFA_WRITE = "SCOPE_${BuiltInClientScopeId.USERS_MFA_WRITE}"
    const val CLIENT_USERS_PROVIDERS_WRITE = "SCOPE_${BuiltInClientScopeId.USERS_PROVIDERS_WRITE}"
    const val CLIENT_INVITATIONS_READ = "SCOPE_${BuiltInClientScopeId.INVITATIONS_READ}"
    const val CLIENT_INVITATIONS_WRITE = "SCOPE_${BuiltInClientScopeId.INVITATIONS_WRITE}"

    private const val SCOPE_PREFIX = "SCOPE_"

    /**
     * Whether [role] is one an access token grants: the role of a [UserAuthentication], of a [ClientAuthentication]
     * or of an administrator, or a scope one of them carries as a role. The state and anonymity are not.
     */
    fun isGrantedByAccessToken(role: String): Boolean =
        role == IS_USER || role == IS_CLIENT || role == IS_ADMIN || role.startsWith(SCOPE_PREFIX)
}
