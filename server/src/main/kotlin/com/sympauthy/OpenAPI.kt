package com.sympauthy

import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.business.model.oauth2.BuiltInClientScopeId
import io.swagger.v3.oas.annotations.ExternalDocumentation
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType.OAUTH2
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.info.License
import io.swagger.v3.oas.annotations.security.*
import io.swagger.v3.oas.annotations.servers.Server
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.inject.Singleton

@OpenAPIDefinition(
    info = Info(
        title = "SympAuthy",
        version = "1.0",
        description = "Self-hosted, open-source OAuth 2.1 and OpenID Connect server. It holds the users a set " +
            "of clients share, issues the tokens those clients trust, and serves the flow an end-user signs in " +
            "through.",
        license = License(
            name = "GNU General Public License v3.0",
            url = "https://www.gnu.org/licenses/gpl-3.0.fr.html#license-text"
        )
    ),
    servers = [
        Server(
            description = "This server, at its configured root URL.",
            url = "{rootUrl}"
        )
    ],
    tags = [
        Tag(
            name = "oauth2",
            description = "OAuth 2.1 endpoints. The browser of an end-user is sent to the authorization " +
                "endpoint, and a client calls the token, introspection and revocation endpoints, authenticating " +
                "with its own credentials.",
            externalDocs = ExternalDocumentation(
                url = "https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-1"
            )
        ),
        Tag(
            name = "openid",
            description = "OpenID Connect endpoints. A client calls them with an access token issued for an " +
                "end-user.",
            externalDocs = ExternalDocumentation(
                url = "https://openid.net/specs/openid-connect-core-1_0.html"
            )
        ),
        Tag(
            name = "openiddiscovery",
            description = "OpenID Connect discovery endpoints, open to any caller: the metadata of this server " +
                "and the keys verifying the tokens it signs.",
            externalDocs = ExternalDocumentation(
                url = "https://openid.net/specs/openid-connect-discovery-1_0.html"
            )
        ),
        Tag(
            name = "flow",
            description = "Endpoints the UI calls to take an end-user through the steps of a flow. A request is " +
                "gated by the `state` identifying its interactive flow session: in the query string of a " +
                "`GET`, and as `Authorization: State <state>` on a `POST`."
        ),
        Tag(
            name = "admin",
            description = "Endpoints an administrator calls, from the admin console or their own tools, to manage " +
                "the users, consents and invitations of this server and to read its configuration and " +
                "interactive flow sessions. Each requires an access token issued to an end-user who was granted " +
                "the admin scope the operation names."
        ),
        Tag(
            name = "client",
            description = "Endpoints a client calls on its own behalf, to read and manage the users of its " +
                "audience and their invitations. Each requires an access token the client obtained with the " +
                "`client_credentials` grant, carrying the scope the operation names."
        ),
    ]
)
@SecuritySchemes(
    value = [
        SecurityScheme(
            name = "admin",
            description = "Access token of an administrator: an end-user who signed in through the " +
                "authorization code grant of a client of the admin audience, and was granted the admin scope " +
                "the operation names.",
            type = OAUTH2,
            flows = OAuthFlows(
                authorizationCode = OAuthFlow(
                    authorizationUrl = "{rootUrl}/api/oauth2/authorize",
                    tokenUrl = "{rootUrl}/api/oauth2/token",
                    refreshUrl = "{rootUrl}/api/oauth2/token",
                    scopes = [
                        OAuthScope(name = AdminScopeId.CONFIG_READ),
                        OAuthScope(name = AdminScopeId.USERS_READ),
                        OAuthScope(name = AdminScopeId.USERS_WRITE),
                        OAuthScope(name = AdminScopeId.USERS_DELETE),
                        OAuthScope(name = AdminScopeId.CONSENT_READ),
                        OAuthScope(name = AdminScopeId.CONSENT_WRITE),
                        OAuthScope(name = AdminScopeId.INTERACTIVE_FLOW_SESSIONS_READ)
                    ]
                )
            )
        ),
        SecurityScheme(
            name = "client",
            description = "Access token a client obtains on its own behalf with the `client_credentials` grant, " +
                "carrying the scopes the operation names.",
            type = OAUTH2,
            flows = OAuthFlows(
                clientCredentials = OAuthFlow(
                    tokenUrl = "{rootUrl}/api/oauth2/token",
                    scopes = [
                        OAuthScope(name = BuiltInClientScopeId.USERS_READ),
                        OAuthScope(name = BuiltInClientScopeId.USERS_CLAIMS_READ),
                        OAuthScope(name = BuiltInClientScopeId.USERS_CLAIMS_WRITE),
                        OAuthScope(name = BuiltInClientScopeId.USERS_MFA_READ),
                        OAuthScope(name = BuiltInClientScopeId.USERS_MFA_WRITE),
                        OAuthScope(name = BuiltInClientScopeId.USERS_PROVIDERS_WRITE)
                    ]
                )
            )
        )
    ]
)
@Singleton
class OpenAPI
