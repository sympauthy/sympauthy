package com.sympauthy.config.validation

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import java.time.Duration

/**
 * [lifetime] where it is greater than zero, or null where it is not — reported to [ctx] under [configKey] as
 * `config.lifetime.not_positive` — or where [lifetime] is null.
 *
 * Zero reads as "no lifetime" and means the opposite: whatever it bounds would end as soon as it began. Every
 * lifetime is held to it here, so an operator reads the same refusal whichever key they wrote.
 */
fun validateLifetime(ctx: ConfigParsingContext, configKey: String, lifetime: Duration?): Duration? {
    if (lifetime == null || lifetime.isPositive) return lifetime
    ctx.addError(configExceptionOf(configKey, "config.lifetime.not_positive", "duration" to lifetime.toString()))
    return null
}
