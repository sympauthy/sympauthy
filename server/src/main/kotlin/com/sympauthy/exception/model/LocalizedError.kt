package com.sympauthy.exception.model

import io.micronaut.http.HttpStatus

/**
 * Represents an error with localized messages.
 */
data class LocalizedError(
    /**
     * The HTTP status code to respond to the end-user.
     */
    val httpStatus: HttpStatus,
    /**
     * A unique error code identifying the specific type of error.
     */
    val errorCode: String,
    /**
     * A message explaining the error to the end-user.
     * It may contain information on how to recover from the issue.
     * It is localized to the end-user's preferred language.
     *
     * Absent when no bundle holds a message under the description code, which is a code shipped
     * without its message rather than a state the caller can do anything about.
     */
    val description: String?,
    /**
     * A message containing technical details about the error.
     * May be absent if the printDetailsInError configuration is disabled.
     */
    val details: String?
)
