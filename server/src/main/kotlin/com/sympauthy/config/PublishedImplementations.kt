package com.sympauthy.config

import kotlin.reflect.KClass

/**
 * The implementations the container published for [type], by the qualifier each is named with.
 *
 * [PublishedImplementationReader] is where the set comes from; nothing declares it.
 */
data class PublishedImplementations<T : Any>(
    val type: KClass<T>,
    val qualifiers: Set<String>
)
