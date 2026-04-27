package com.sympauthy.config.properties

import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter

/**
 * Configuration of a scope template that defines default values for scopes.
 *
 * Templates following the naming convention `default_<type>` (e.g. `default_openid`, `default_custom`)
 * are automatically applied based on the scope type when no explicit template is specified.
 * Custom templates can be referenced by name via the `template` property on a scope.
 */
@EachProperty(ScopeTemplateConfigurationProperties.TEMPLATES_SCOPES_KEY)
class ScopeTemplateConfigurationProperties(
    @param:Parameter val id: String
) {
    val enabled: String? = null
    var discoverable: String? = null
    var type: String? = null
    var audience: String? = null

    companion object {
        const val TEMPLATES_SCOPES_KEY = "templates.scopes"

        const val DEFAULT_OPENID = "default_openid"
        const val DEFAULT_ADMIN = "default_admin"
        const val DEFAULT_CLIENT = "default_client"
        const val DEFAULT_CUSTOM = "default_custom"

        /**
         * Set of all default template names that are applied implicitly and cannot be referenced
         * directly via the `template` directive.
         */
        val DEFAULT_TEMPLATE_NAMES = setOf(
            DEFAULT_OPENID, DEFAULT_ADMIN, DEFAULT_CLIENT, DEFAULT_CUSTOM
        )
    }
}
