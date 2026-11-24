package com.sympauthy.platform.util

import java.util.*

/**
 * The default locale used when:
 * - the locale of the end-user cannot be determined.
 * - a translation of a message is not available in end-user locale.
 */
val DEFAULT_LOCALE = Locale.US

/**
 * Return the [Locale] contained in the [Optional] or the hardcoded default.
 */
fun Optional<Locale>.orDefault(): Locale = this.orElse(DEFAULT_LOCALE)
