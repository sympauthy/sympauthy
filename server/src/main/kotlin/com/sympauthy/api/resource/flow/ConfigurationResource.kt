package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = """
For ALL URLs contained in this configuration, the state query param must be added by the client before using any
of those urls.
    """
)
@Serdeable
data class ConfigurationResource(
    @get:Schema(
        description = "List of claims collectable by the authorization server."
    )
    val claims: List<CollectableClaimConfigurationResource>,
    val features: FeaturesResource,
    val password: PasswordConfigurationResource?,
    @get:Schema(
        description = "List of configuration of third-party providers."
    )
    val providers: List<ProviderConfigurationResource>?
)

@Schema(
    description = """
Configuration related to a claim collectable by this authorization server.

A collectable claim is:
- a claim which value is expected to be inputted by the end-user. (ex. the end-user email)

Custom claim are not considered collectable by default. You need to configure a custom claim as explicitly ```collectable```
through the configuration if you want your custom claims to be collected by the authorization flow.
"""
)
@Serdeable
data class CollectableClaimConfigurationResource(
    @get:Schema(
        description = "Identifier of the claim."
    )
    val id: String,
    @get:Schema(
        description = """
Whether the collection of the claim is required to complete the authorization flow?

Required claim must be asked to the end-user and a non-empty value is expected.
The authorization server will not let the authentication flow to complete if one of the required claim is missing.
Also the authorization server may present again the authorization flow to the user if a claim become required.

Non-required claim will not impact the end of the authorization flow. 
It is up to the flow implementation to decide whether they will be presented and asked to the end-user.
        """
    )
    val required: Boolean,
    @get:Schema(
        description = "Localized name of the claim."
    )
    val name: String,
    @get:Schema(
        description = """
Type of the value accepted for the claim. 

Supported values are:
- ```string```
- ```number```
- ```date```
        """
    )
    val type: String,
    @get:Schema(
        description = """
Identifier of the group the claim is part of. Claims sharing the same group are related one to another.
Ex. first name & last name are related to the identity of the end-user.
        """
    )
    val group: String?
)

@Schema(
    description = "List of boolean flags indicating which features are enabled on this authorization server."
)
@Serdeable
data class FeaturesResource(
    @get:Schema(
        description = "Authentication of the end-user using a login and a password couple."
    )
    @get:JsonProperty("password_sign_in")
    val passwordSignIn: Boolean,
    @get:Schema(
        description = "End-user account creation."
    )
    @get:JsonProperty("sign_up")
    val signUp: Boolean,
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
