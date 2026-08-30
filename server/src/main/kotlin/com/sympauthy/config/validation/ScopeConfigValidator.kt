package com.sympauthy.config.validation

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.oauth2.AdminScope
import com.sympauthy.business.model.oauth2.BuiltInClientScope
import com.sympauthy.business.model.oauth2.BuiltInGrantableScope
import com.sympauthy.business.model.oauth2.ClientScope
import com.sympauthy.business.model.oauth2.ConsentableUserScope
import com.sympauthy.business.model.oauth2.GrantableUserScope
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.oauth2.isAdminScope
import com.sympauthy.business.model.oauth2.isBuiltInClientScope
import com.sympauthy.business.model.oauth2.isBuiltInGrantableScope
import com.sympauthy.business.model.user.OpenIdConnectScope
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.parsing.ParsedScopeConfig
import com.sympauthy.config.parsing.ParsedScopeSetting
import com.sympauthy.config.properties.ScopeConfigurationProperties.Companion.SCOPES_KEY
import com.sympauthy.config.properties.ScopeTemplateConfigurationProperties.Companion.TEMPLATES_SCOPES_KEY
import jakarta.inject.Singleton

/**
 * Assembles the complete set of scopes the server serves.
 *
 * A deployment only ever adds custom scopes and turns OpenID Connect ones off; every other scope is
 * one the server defines itself and no configuration can name. Both halves are put together here so
 * that nothing downstream has to know the difference.
 */
@Singleton
class ScopeConfigValidator {

    private val builtInGrantableScopes: List<Scope> = BuiltInGrantableScope.entries.map { builtIn ->
        GrantableUserScope(scope = builtIn.scope, discoverable = builtIn.discoverable)
    }

    private val clientScopes: List<Scope> = BuiltInClientScope.entries.map { builtIn ->
        ClientScope(scope = builtIn.scope)
    }

    /**
     * Build every scope the server serves.
     *
     * [adminAudienceId] is the audience the administration scopes are bound to, or null when the
     * deployment configured no administration API, in which case none of them are served.
     */
    fun validate(
        ctx: ConfigParsingContext,
        parsed: List<ParsedScopeConfig>,
        audiencesById: Map<String, Audience>,
        adminAudienceId: String?
    ): List<Scope> {
        val configurable = parsed.filter { isConfigurable(ctx, it) }

        val disabledOpenIdConnectScopes = configurable
            .filter { it.isOpenIdConnect }
            .mapNotNull { validateOpenIdConnectScope(ctx, it) }
            .toSet()
        val customScopes = configurable
            .filterNot { it.isOpenIdConnect }
            .mapNotNull { validateCustomScope(ctx, it, audiencesById) }

        return builtInGrantableScopes +
                adminScopes(adminAudienceId) +
                clientScopes +
                openIdConnectScopes(disabledOpenIdConnectScopes) +
                customScopes
    }

    /**
     * Whether the deployment is allowed to configure the scope at all.
     * A scope the server defines itself is reported as an error rather than silently ignored.
     */
    private fun isConfigurable(
        ctx: ConfigParsingContext,
        parsed: ParsedScopeConfig
    ): Boolean {
        val configKeyPrefix = "$SCOPES_KEY.${parsed.id}"

        if (parsed.id.isAdminScope()) {
            ctx.addError(
                configExceptionOf(configKeyPrefix, "config.scope.admin_not_configurable", "scope" to parsed.id)
            )
            return false
        }
        if (parsed.id.isBuiltInGrantableScope() || parsed.id.isBuiltInClientScope()) {
            ctx.addError(
                configExceptionOf(configKeyPrefix, "config.scope.builtin_not_configurable", "scope" to parsed.id)
            )
            return false
        }
        return true
    }

    /**
     * Return the identifier of the OpenID Connect scope when the deployment turned it off,
     * otherwise null.
     */
    private fun validateOpenIdConnectScope(
        ctx: ConfigParsingContext,
        parsed: ParsedScopeConfig
    ): String? {
        refuseSetting(
            ctx, parsed, parsed.audience, "audience",
            onEntry = "config.scope.audience.not_allowed_for_openid",
            onTemplate = "config.scope.template.audience_not_allowed_for_openid"
        )
        refuseSetting(
            ctx, parsed, parsed.type, "type",
            onEntry = "config.scope.type.not_allowed_for_openid",
            onTemplate = "config.scope.template.type_not_allowed_for_openid"
        )
        return if (parsed.enabled == false) parsed.id else null
    }

    /**
     * Record the error for a [setting] that reached an OpenID Connect scope but cannot apply to
     * one, on the line the deployment wrote it: [settingKey] under the scope's own entry, or the
     * reference to the template supplying it.
     *
     * The reference is named rather than the template, because a template carrying the setting is
     * legitimate for the custom scopes also using it — the mistake is pointing an OpenID Connect
     * scope at it. The template applied implicitly to these scopes cannot arrive here at all: what
     * it carries is refused where it is declared, which leaves the whole configuration unusable
     * before any scope is built from it.
     */
    private fun refuseSetting(
        ctx: ConfigParsingContext,
        parsed: ParsedScopeConfig,
        setting: ParsedScopeSetting?,
        settingKey: String,
        onEntry: String,
        onTemplate: String
    ) {
        val templateId = (setting ?: return).templateId
        val error = if (templateId == null) {
            configExceptionOf(
                "$SCOPES_KEY.${parsed.id}.$settingKey",
                onEntry,
                "scope" to parsed.id
            )
        } else {
            configExceptionOf(
                "$SCOPES_KEY.${parsed.id}.template",
                onTemplate,
                "scope" to parsed.id,
                "template" to templateId
            )
        }
        ctx.addError(error)
    }

    private fun validateCustomScope(
        ctx: ConfigParsingContext,
        parsed: ParsedScopeConfig,
        audiencesById: Map<String, Audience>
    ): Scope? {
        val configKeyPrefix = "$SCOPES_KEY.${parsed.id}"

        val audienceId = validateAudienceId(
            ctx, parsed.audience?.value, audiencesById,
            audienceKey(parsed), "config.scope.audience.not_found"
        )

        val type = parsed.type?.value
        val consentable = when (type) {
            null, "grantable" -> false
            "consentable" -> true
            "client" -> {
                ctx.addError(
                    configExceptionOf(
                        "$configKeyPrefix.type",
                        "config.scope.custom_client_type_not_allowed",
                        "scope" to parsed.id
                    )
                )
                return null
            }

            else -> {
                ctx.addError(
                    configExceptionOf(
                        "$configKeyPrefix.type",
                        "config.scope.invalid_type",
                        "scope" to parsed.id,
                        "type" to type
                    )
                )
                return null
            }
        }

        return if (consentable) {
            ConsentableUserScope(scope = parsed.id, discoverable = true, audienceId = audienceId)
        } else {
            GrantableUserScope(scope = parsed.id, discoverable = true, audienceId = audienceId)
        }
    }

    /**
     * The key the audience of [parsed] was written at, which is the template it was inherited from
     * when the scope's own entry did not name one.
     */
    private fun audienceKey(parsed: ParsedScopeConfig): String {
        val templateId = parsed.audience?.templateId
            ?: return "$SCOPES_KEY.${parsed.id}.audience"
        return "$TEMPLATES_SCOPES_KEY.$templateId.audience"
    }

    private fun openIdConnectScopes(disabledScopes: Set<String>): List<Scope> {
        return OpenIdConnectScope.entries
            .filterNot { it.scope in disabledScopes }
            .map { ConsentableUserScope(scope = it.scope, discoverable = true) }
    }

    private fun adminScopes(adminAudienceId: String?): List<Scope> {
        if (adminAudienceId == null) return emptyList()
        return AdminScope.entries.map { adminScope ->
            GrantableUserScope(
                scope = adminScope.scope,
                discoverable = false,
                audienceId = adminAudienceId
            )
        }
    }
}
