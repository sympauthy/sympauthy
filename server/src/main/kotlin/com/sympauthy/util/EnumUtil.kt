package com.sympauthy.util

/**
 * The name this value is published under, and the one a failure naming it interpolates.
 */
val Enum<*>.wireName: String
    get() = name.lowercase()

/**
 * The name this value is spelled with in the configuration file: lowercase, and words separated by a dash.
 */
val Enum<*>.configName: String
    get() = name.lowercase().replace("_", "-")
