package com.sympauthy

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
        description = "A self-served, open-source authorization server.",
        license = License(
            name = "GNU General Public License v3.0",
            url = "https://www.gnu.org/licenses/gpl-3.0.fr.html#license-text"
        )
    ),
    servers = [
        Server(
            description = "The server serving this documentation.",
            url = "{rootUrl}"
        )
    ],
    tags = [
        Tag(
            name = "oauth2",
            description = "OAuth 2.1",
            externalDocs = ExternalDocumentation(
                url = "https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-1"
            )
        ),
        Tag(
            name = "openid",
            description = "OpenID Connect Core 1.0",
            externalDocs = ExternalDocumentation(
                url = "https://openid.net/specs/openid-connect-core-1_0.html"
            )
        ),
        Tag(
            name = "openiddiscovery",
            description = "OpenID Connect Discovery 1.0",
            externalDocs = ExternalDocumentation(
                url = "https://openid.net/specs/openid-connect-discovery-1_0.html"
            )
        ),
        Tag(
            name = "flow"
        ),
    ]
)
@SecuritySchemes(
    value = [
        SecurityScheme(
            name = "SympAuthy",
            description = "Authenticate to this authorization server to access its protected resources.",
            type = OAUTH2,
            flows = OAuthFlows(
                authorizationCode = OAuthFlow(
                    authorizationUrl = "{rootUrl}/api/oauth2/authorize",
                    tokenUrl = "{rootUrl}/api/oauth2/token",
                    refreshUrl = "{rootUrl}/api/oauth2/token",
                    scopes = [
                        OAuthScope(name = "profile")
                    ]
                )
            )
        )
    ]
)
@Singleton
class OpenAPI