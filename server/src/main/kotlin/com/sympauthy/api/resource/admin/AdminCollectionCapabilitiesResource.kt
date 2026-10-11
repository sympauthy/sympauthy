package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "What a collection accepts: the fields it filters, orders and searches on, and the order it " +
            "takes when none is asked for. A field this document does not name is refused."
)
@Serdeable
data class AdminCollectionCapabilitiesResource(
    @get:Schema(
        description = "Free-text search the collection offers. Absent when the collection searches nothing, " +
                "and `q` is then refused."
    )
    @get:JsonProperty("search")
    val search: AdminCollectionSearchResource?,
    @get:Schema(
        description = "Fields the collection filters on, each with the operators it accepts."
    )
    @get:JsonProperty("filters")
    val filters: List<AdminCollectionFilterResource>,
    @get:Schema(
        description = "Fields the collection orders on, which are the keys `sort` accepts."
    )
    @get:JsonProperty("sorts")
    val sorts: List<AdminCollectionSortResource>,
    @get:Schema(
        description = "Order the collection takes when none is asked for, written the way `sort` is. Absent " +
                "when the collection orders only by the key no two items share."
    )
    @get:JsonProperty("default_sort")
    val defaultSort: String?
)

@Schema(
    description = "Free-text search a collection offers, matched partially and ignoring case."
)
@Serdeable
data class AdminCollectionSearchResource(
    @get:Schema(description = "Names of the fields `q` matches against.")
    @get:JsonProperty("fields")
    val fields: List<String>
)

@Schema(
    description = "A field a collection filters on."
)
@Serdeable
data class AdminCollectionFilterResource(
    @get:Schema(description = "Name of the field, as it is sent in the query string.")
    @get:JsonProperty("field")
    val field: String,
    @get:Schema(
        description = "Label of the field, in the language the request asked for. It may be reworded in any " +
                "release."
    )
    @get:JsonProperty("name")
    val name: String,
    @get:Schema(
        description = "Type of value the field holds, which decides the operators it accepts and how a value " +
                "sent for it is read.",
        allowableValues = [
            "string", "email", "phone_number", "timezone", "uuid", "enum", "boolean", "number", "date", "date_time"
        ]
    )
    @get:JsonProperty("type")
    val type: String,
    @get:ArraySchema(
        arraySchema = Schema(
            description = "Operators the field accepts, each sent as a suffix on the field name. A bare " +
                "`field=value` is `eq`."
        ),
        schema = Schema(
            allowableValues = [
                "eq",
                "ne",
                "lt",
                "lte",
                "gt",
                "gte",
                "in",
                "contains",
                "starts_with",
                "is_null"
            ]
        )
    )
    @get:JsonProperty("operators")
    val operators: List<String>,
    @get:Schema(
        description = "Values the field holds. Absent when the field takes any value of its type."
    )
    @get:JsonProperty("values")
    val values: List<AdminCollectionFilterValueResource>?
)

@Schema(
    description = "A value a field over a closed set holds."
)
@Serdeable
data class AdminCollectionFilterValueResource(
    @get:Schema(description = "The value, as it is sent in the query string.")
    @get:JsonProperty("value")
    val value: String,
    @get:Schema(
        description = "Label of the value, in the language the request asked for. It may be reworded in any " +
                "release."
    )
    @get:JsonProperty("name")
    val name: String
)

@Schema(
    description = "A field a collection orders on."
)
@Serdeable
data class AdminCollectionSortResource(
    @get:Schema(description = "Key naming the field in `sort`.")
    @get:JsonProperty("field")
    val field: String,
    @get:Schema(
        description = "Label of the field, in the language the request asked for. It may be reworded in any " +
                "release."
    )
    @get:JsonProperty("name")
    val name: String
)
