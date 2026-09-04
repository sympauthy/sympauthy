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
    val details: String?,
    /**
     * The failure of each property of the payload the error refuses, one entry per property.
     *
     * Empty for every failure but a validation of several properties at once, which is the only
     * thing that reports more than one failure.
     */
    val properties: List<LocalizedPropertyError> = emptyList()
)

/**
 * Represents the failure of one property of the payload, localized the same way [LocalizedError] is.
 *
 * It is the error's own triple minus the status: the property is refused by the response the error
 * describes, and carries no status of its own.
 */
data class LocalizedPropertyError(
    /**
     * The path to the property of the payload that failed.
     */
    val path: String,
    /**
     * A unique error code identifying the specific type of error.
     */
    val errorCode: String,
    /**
     * A message explaining to the end-user why this property was refused, localized to their
     * preferred language.
     *
     * Absent where the failure names no description, and not filled in with the generic sentence
     * [LocalizedError.description] falls back to: an unexpected-error sentence printed against one
     * property says less than [path] and [errorCode] already do.
     */
    val description: String?,
    /**
     * A message containing technical details about the error.
     * May be absent if the printDetailsInError configuration is disabled.
     */
    val details: String?
)
