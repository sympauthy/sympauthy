package com.sympauthy.config.properties

import com.sympauthy.config.properties.BootstrapInvitationConfigurationProperties.Companion.INVITATIONS_KEY
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter

/**
 * Configuration for a bootstrap invitation declared in YAML.
 *
 * Bootstrap invitations are created at startup when no user has yet consented
 * for the configured audience, allowing the first user to self-register.
 *
 * Example:
 * ```yaml
 * invitations:
 *   first-admin:
 *     audience: admin
 *     url-template: "https://admin.example.com/register?invitation_token={token}"
 *     claims:
 *       role: admin
 *     note: Initial admin invitation
 * ```
 */
@EachProperty(INVITATIONS_KEY)
class BootstrapInvitationConfigurationProperties(
    @param:Parameter val id: String
) {
    var audience: String? = null
    var urlTemplate: String? = null
    var claims: Map<String, String>? = null
    var note: String? = null

    companion object {
        const val INVITATIONS_KEY = "invitations"
    }
}
