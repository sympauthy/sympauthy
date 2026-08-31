package com.sympauthy.util

/**
 * The name this value is published under, and the one a failure naming it interpolates.
 */
val Enum<*>.wireName: String
    get() = name.lowercase()
