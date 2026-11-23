package com.sympauthy.config.validation

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import java.time.Duration

/**
 * [duration] where it is greater than zero, or null where it is not — reported to [ctx] under
 * [configKey] as `config.duration.not_positive` — or where [duration] is null.
 */
fun validatePositiveDuration(ctx: ConfigParsingContext, configKey: String, duration: Duration?): Duration? {
    if (duration == null || duration.isPositive) return duration
    ctx.addError(configExceptionOf(configKey, "config.duration.not_positive", "duration" to duration.toString()))
    return null
}
