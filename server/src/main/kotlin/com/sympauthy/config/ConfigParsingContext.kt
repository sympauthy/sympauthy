package com.sympauthy.config

import com.sympauthy.config.exception.ConfigurationException

/**
 * Accumulates [ConfigurationException]s across parsing and validation phases: a factory creates
 * one, and its parser and validator record into it, as
 * `docs/standards/config-layer-code-standard.md` lays the factory out.
 */
class ConfigParsingContext {
    private val _errors = mutableListOf<ConfigurationException>()

    val errors: List<ConfigurationException> get() = _errors.toList()

    val hasErrors: Boolean get() = _errors.isNotEmpty()

    /**
     * Execute [block] and return its result.
     * If [block] throws a [ConfigurationException], the exception is accumulated and null is returned.
     */
    fun <T> parse(block: () -> T): T? {
        return try {
            block()
        } catch (e: ConfigurationException) {
            _errors.add(e)
            null
        }
    }

    /**
     * Add a validation error discovered outside of a [parse] block.
     */
    fun addError(error: ConfigurationException) {
        _errors.add(error)
    }

    /**
     * Merge all errors from another context into this one.
     * Useful when a sub-section (e.g., HashConfig) is parsed with its own context
     * and the errors need to be bubbled up.
     */
    fun merge(other: ConfigParsingContext) {
        _errors.addAll(other._errors)
    }

    /**
     * Create a child context for parsing a sub-section.
     * Errors can be inspected independently or merged back into this context via [merge].
     */
    fun child(): ConfigParsingContext = ConfigParsingContext()
}
