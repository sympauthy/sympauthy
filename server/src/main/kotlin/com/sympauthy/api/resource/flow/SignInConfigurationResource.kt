package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = """
Sign-in capabilities exposed by this authorization server: the authentication methods the end-user
can use to sign in (password and/or third-party providers).

For ALL URLs contained in this configuration, the state query param must be added by the client
before using any of those urls.
    """
)
@Serdeable
data class SignInConfigurationResource(
    @get:Schema(
        description = "Authentication of the end-user using a login and a password couple."
    )
    @get:JsonProperty("password_sign_in")
    val passwordSignIn: Boolean,
    val password: PasswordConfigurationResource?,
    @get:Schema(
        description = "List of configuration of third-party providers."
    )
    val providers: List<ProviderConfigurationResource>?
)

@Schema(
    description = """
If null or not present, the authentication by password is disabled by the authorization server.
"""
)
@Serdeable
data class PasswordConfigurationResource(
    @get:Schema(
        name = "identifier_claims",
        description = "List of claims that uniquely identify a user. Used as login for sign-in and as required claims for sign-up."
    )
    @get:JsonProperty("identifier_claims")
    val identifierClaims: List<String>,
)

@Schema(
    description = "Configuration related to a third-party provider that can be used by the end-user to authenticate."
)
@Serdeable
data class ProviderConfigurationResource(
    @get:Schema(
        description = "Identifier of the third-party provider."
    )
    @get:JsonProperty("id")
    val id: String,
    @get:Schema(
        description = "Name of the third-party provider as it should be displayed to the end-user."
    )
    @get:JsonProperty("name")
    val name: String,
    @get:Schema(
        name = "authorize_url",
        description = """
URL to redirect the end-user to to initiate a authorization grant flow with the third-party provider.
        """
    )
    @get:JsonProperty("authorize_url")
    val authorizeUrl: String
)
