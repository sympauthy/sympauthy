package com.sympauthy.config.exception

open class ConfigurationException(
    val key: String,
    val messageId: String,
    val values: Map<String, Any?> = emptyMap()
): Exception("Config - $key - $messageId")

fun configExceptionOf(
    key: String,
    messageId: String,
    vararg values: Pair<String, Any?>
) = ConfigurationException(key, messageId, mapOf(*values))
