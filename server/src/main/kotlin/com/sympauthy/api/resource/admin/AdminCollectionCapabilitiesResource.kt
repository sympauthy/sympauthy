package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "What one collection accepts: the fields it filters on, the fields it orders on, the " +
            "fields a free-text search matches against, and the order it takes when the caller names " +
            "none. A field absent from this document is a field the collection refuses."
)
@Serdeable
data class AdminCollectionCapabilitiesResource(
    @get:Schema(
        description = "The fields a free-text q matches against. Absent where the collection answers no q at all."
    )
    @get:JsonProperty("search")
    val search: AdminCollectionSearchResource?,
    @get:Schema(
        description = "The fields the collection filters on, each with the operators it admits."
    )
    @get:JsonProperty("filters")
    val filters: List<AdminCollectionFilterResource>,
    @get:Schema(
        description = "The fields the collection orders on, which are the keys the sort parameter accepts."
    )
    @get:JsonProperty("sorts")
    val sorts: List<AdminCollectionSortResource>,
    @get:Schema(
        description = "The order the collection takes when the caller names none, spelled the way sort is " +
                "spelled. Absent where the collection's own order is the unique key it ends on, which is " +
                "never a key a caller may name."
    )
    @get:JsonProperty("default_sort")
    val defaultSort: String?
)

@Schema(
    description = "The fields a free-text q matches against, partially and ignoring case."
)
@Serdeable
data class AdminCollectionSearchResource(
    @get:Schema(description = "The wire names of the fields q matches against.")
    @get:JsonProperty("fields")
    val fields: List<String>
)

@Schema(
    description = "One field the collection filters on."
)
@Serdeable
data class AdminCollectionFilterResource(
    @get:Schema(description = "The name the field is sent under in the query string.")
    @get:JsonProperty("field")
    val field: String,
    @get:Schema(
        description = "The name the field is read under, in the language the request asked for. It may be " +
                "reworded in any release, so nothing may branch on it."
    )
    @get:JsonProperty("name")
    val name: String,
    @get:Schema(
        description = "What kind of value the field holds: string, email, phone_number, timezone, uuid, " +
                "enum, boolean, number, date or date_time."
    )
    @get:JsonProperty("type")
    val type: String,
    @get:Schema(
        description = "The operators this field admits, each written as a dotted suffix on the field name. " +
                "A bare field=value is eq. These words are not localized."
    )
    @get:JsonProperty("operators")
    val operators: List<String>,
    @get:Schema(
        description = "The values this field holds, where the set is closed and belongs to this deployment. " +
                "Absent where the set is open, and a caller then sends a value of the field's own type."
    )
    @get:JsonProperty("values")
    val values: List<AdminCollectionFilterValueResource>?
)

@Schema(
    description = "One value a field over a closed set holds."
)
@Serdeable
data class AdminCollectionFilterValueResource(
    @get:Schema(description = "The value as it is sent in the query string.")
    @get:JsonProperty("value")
    val value: String,
    @get:Schema(
        description = "The name the value is read under, in the language the request asked for. It may be " +
                "reworded in any release, so nothing may branch on it."
    )
    @get:JsonProperty("name")
    val name: String
)

@Schema(
    description = "One field the collection orders on."
)
@Serdeable
data class AdminCollectionSortResource(
    @get:Schema(
        description = "The key the field is named by in the sort parameter, prefixed with - to read it " +
                "from the largest value to the smallest."
    )
    @get:JsonProperty("field")
    val field: String,
    @get:Schema(
        description = "The name the field is read under, in the language the request asked for. It may be " +
                "reworded in any release, so nothing may branch on it."
    )
    @get:JsonProperty("name")
    val name: String
)
