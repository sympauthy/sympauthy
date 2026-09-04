package com.sympauthy.util

/**
 * Implemented by an enum holding a value the lowercased Kotlin name does not spell: the value
 * declares what it is published under, and [wireName] reads the declaration.
 *
 * A value the rule reaches declares nothing, so the declaration that is left reads as what it is —
 * this one value is spelled differently on purpose.
 */
interface PublishedUnderAnotherName {
    /**
     * The name this value is published under, or null where lowercasing its Kotlin name gives it.
     */
    val publishedName: String?
}

/**
 * The name this value is published under, and the one a failure naming it interpolates.
 */
val Enum<*>.wireName: String
    get() = (this as? PublishedUnderAnotherName)?.publishedName ?: name.lowercase()

/**
 * The name this value is spelled with in the configuration file: lowercase, and words separated by a dash.
 */
val Enum<*>.configName: String
    get() = name.lowercase().replace("_", "-")
