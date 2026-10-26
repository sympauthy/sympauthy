package com.sympauthy.monitoring

/**
 * A dependency the server cannot fully work without, reported operational or degraded by [Conditions].
 *
 * This declaration is the authority on which conditions exist: anything reached outside this process on
 * a request's behalf, and the console's own queue, whose dropping a line is the server failing to say
 * what it was asked to.
 *
 * A condition is published on `/health` by its [name] alone, and the names say nothing the sign-in pages
 * do not.
 */
sealed class Condition {

    /**
     * The name the condition is published under, unique among the conditions of its kind.
     */
    abstract val name: String

    /**
     * What a line about the condition opens with.
     */
    internal abstract val subject: String

    /**
     * The sentence the ERROR opens with, saying what stopped working.
     */
    internal open val failingMessage: String get() = "$subject is failing"

    /**
     * [count] failures, worded as what failed.
     */
    internal open fun describeFailures(count: Long): String =
        "absorbed $count ${if (count == 1L) "failure" else "failures"}"

    data object Database : Condition() {
        override val name = "database"
        override val subject = "The database"
    }

    data object Mail : Condition() {
        override val name = "mail"
        override val subject = "The mail sender"
    }

    /**
     * The queue every line below ERROR goes through on its way to the console. Its failures are the lines
     * it dropped.
     */
    data object Console : Condition() {
        override val name = "console"
        override val subject = "The console's queue"
        override val failingMessage = "The console's queue is full and dropping lines below ERROR"

        override fun describeFailures(count: Long): String =
            "dropped $count ${if (count == 1L) "line" else "lines"}"
    }

    data class Provider(val providerId: String) : Condition() {
        override val name get() = providerId
        override val subject get() = "Provider '$providerId'"
    }

    data class AuthorizationWebhook(val clientId: String) : Condition() {
        override val name get() = clientId
        override val subject get() = "The authorization webhook of client '$clientId'"
    }
}
