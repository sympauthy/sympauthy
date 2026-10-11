package com.sympauthy.api.resource.flow

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "A claim the flow asks the end-user for."
)
@Serdeable
data class CollectableClaimResource(
    @get:Schema(
        description = "Identifier of the claim."
    )
    val id: String,
    @get:Schema(
        description = "Whether the end-user must provide a value for this claim before the flow completes."
    )
    val required: Boolean,
    @get:Schema(
        description = "Name of the claim to show the end-user, in the language the request asks for, or its " +
            "identifier when no name is defined for it."
    )
    val name: String,
    @get:Schema(
        description = "Data type of the claim's value.",
        allowableValues = ["boolean", "date", "email", "number", "phone_number", "string", "timezone"]
    )
    val type: String,
    @get:Schema(
        description = "Group of related claims this claim belongs to. Absent when it belongs to none.",
        allowableValues = ["identity", "address"]
    )
    val group: String?
)
