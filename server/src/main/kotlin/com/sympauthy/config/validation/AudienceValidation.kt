package com.sympauthy.config.validation

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf

/**
 * The audience [audienceId] names, or null where it names none — reported to [ctx] under [configKey] with
 * [notFoundMessageId] — or where [audienceId] is null.
 */
fun resolveAudience(
    ctx: ConfigParsingContext,
    audienceId: String?,
    audiencesById: Map<String, Audience>,
    configKey: String,
    notFoundMessageId: String
): Audience? {
    if (audienceId == null) return null
    val audience = audiencesById[audienceId]
    if (audience == null) {
        ctx.addError(
            configExceptionOf(
                configKey,
                notFoundMessageId,
                "audience" to audienceId,
                "availableAudiences" to audiencesById.keys.joinToString(", ")
            )
        )
    }
    return audience
}
