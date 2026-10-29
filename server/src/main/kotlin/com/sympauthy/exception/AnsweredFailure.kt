package com.sympauthy.exception

import io.micronaut.http.HttpStatus

/**
 * A failure answered with an error code, and with a status where it decides one, whichever layer raised it.
 *
 * It is what lets [toOutcome] say how an event ended without knowing the exception of each layer.
 */
interface AnsweredFailure {

    /**
     * The error code the caller is answered with.
     */
    val detailsId: String

    /**
     * Whether the caller can get out of the failure by an action of its own.
     */
    val recoverable: Boolean

    /**
     * The status the failure is answered with, or null where it leaves the status to whoever answers.
     */
    val answeredStatus: HttpStatus?
}
