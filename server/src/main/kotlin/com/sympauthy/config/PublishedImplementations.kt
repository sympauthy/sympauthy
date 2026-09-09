package com.sympauthy.config

import kotlin.reflect.KClass

/**
 * The implementations the container published for [type], by the qualifier each is named with.
 *
 * Those qualifiers are the complete set of words a deployment may write where a setting selects one
 * of them. [PublishedImplementationReader] is where the set comes from; nothing declares it.
 */
data class PublishedImplementations<T : Any>(
    val type: KClass<T>,
    val qualifiers: Set<String>
)
