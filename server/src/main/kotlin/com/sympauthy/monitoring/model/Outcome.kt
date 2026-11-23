package com.sympauthy.monitoring.model

/**
 * How an event a metric counts ended, recorded under `outcome`, and why it did not succeed, recorded under
 * `error.type`.
 *
 * [Refused] is an ending the caller caused — a wrong password, a code already exchanged — and [Error] the
 * server or a dependency failing. See `docs/design/telemetry.md`.
 */
sealed class Outcome(
    internal val value: String
) {

    /**
     * The error code the caller was answered with, or the class of the failure where it carried none. Null on
     * a success.
     */
    abstract val errorType: String?

    data object Success : Outcome("success") {
        override val errorType: String? = null
    }

    data class Refused(override val errorType: String) : Outcome("refused")

    data class Error(override val errorType: String) : Outcome("error")

    companion object {

        /**
         * An [Error] for a [failure] that carries no code, typed by its class as the semantic conventions type
         * one.
         */
        fun errorOf(failure: Throwable): Error = Error(failure.javaClass.name)
    }
}
