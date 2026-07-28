package com.sympauthy.api.resource.flow

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

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
data class CollectableClaimResource(
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
