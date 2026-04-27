package com.sympauthy.config.validation

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf

/**
 * Validate that an audience ID references an existing audience.
 * Returns the audience ID if valid, null otherwise.
 */
fun validateAudienceId(
    ctx: ConfigParsingContext,
    audienceId: String?,
    audiencesById: Map<String, Audience>,
    configKey: String,
    notFoundMessageId: String
): String? {
    if (audienceId == null) return null
    if (audienceId !in audiencesById) {
        ctx.addError(
            configExceptionOf(
                configKey,
                notFoundMessageId,
                "audience" to audienceId,
                "availableAudiences" to audiencesById.keys.joinToString(", ")
            )
        )
        return null
    }
    return audienceId
}
