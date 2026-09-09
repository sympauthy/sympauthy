package com.sympauthy.config.model

import kotlin.reflect.KClass

/**
 * The implementation of [type] a deployment selected, named by the qualifier it is published under.
 */
data class ConfiguredImplementation<T : Any>(
    val type: KClass<T>,
    val qualifier: String
) {

    /**
     * The implementation this names, out of the ones the container injected as [implementations].
     *
     * Throws where the map holds none under this qualifier, which the configuration refusing an
     * unknown word and [com.sympauthy.config.PublishedImplementationReader] refusing an unnamed
     * implementation are between them what rules out.
     */
    fun resolve(implementations: Map<String, T>): T = implementations.getValue(qualifier)
}
