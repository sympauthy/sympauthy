package com.sympauthy.config.model

import kotlin.reflect.KClass

/**
 * The implementation of [type] a deployment selected, named by the qualifier it is published under.
 *
 * It carries the interface it was selected from so that a manager is handed the selection made for
 * its own interface: a bare qualifier would be satisfied just as well by the word another setting
 * of this shape was configured with.
 */
data class ConfiguredImplementation<T : Any>(
    val type: KClass<T>,
    val qualifier: String
) {

    /**
     * The implementation this names, out of the ones the container injected as [implementations].
     *
     * A word naming none of them was refused before the server reported itself ready, and the same
     * container answers what is published and what is injected, so this qualifier is one of the
     * map's keys.
     */
    fun resolve(implementations: Map<String, T>): T = implementations.getValue(qualifier)
}
