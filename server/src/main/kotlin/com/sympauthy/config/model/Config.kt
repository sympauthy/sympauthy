package com.sympauthy.config.model

import com.sympauthy.exception.LocalizedException
import com.sympauthy.exception.localizedExceptionOf

/**
 * Base class of every configuration model other code injects. Each domain is a sealed type with an
 * enabled variant and a disabled one carrying the [configurationErrors] that stopped the enabled
 * one being built, and a consumer narrows it as `docs/standards/config-layer-code-standard.md`
 * says.
 */
open class Config(
    /**
     * List of errors that we detected in the configuration when parsing it and validating it.
     */
    val configurationErrors: List<Exception>? = null
)

val Config.invalidConfig: LocalizedException
    get() = localizedExceptionOf("config.invalid")
