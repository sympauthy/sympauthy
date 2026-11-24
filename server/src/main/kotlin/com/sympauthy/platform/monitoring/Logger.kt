package com.sympauthy.platform.monitoring

import org.slf4j.LoggerFactory

/**
 * The logger of this server, which takes every message as a lambda evaluated only once its level is known
 * to be enabled, and offers no overload taking a built string. See `docs/design/logging.md`.
 */
class Logger(
    @PublishedApi internal val delegate: org.slf4j.Logger
) {

    inline fun trace(message: () -> String) {
        if (delegate.isTraceEnabled) delegate.trace(message())
    }

    inline fun debug(message: () -> String) {
        if (delegate.isDebugEnabled) delegate.debug(message())
    }

    inline fun debug(throwable: Throwable, message: () -> String) {
        if (delegate.isDebugEnabled) delegate.debug(message(), throwable)
    }

    inline fun info(message: () -> String) {
        if (delegate.isInfoEnabled) delegate.info(message())
    }

    inline fun warn(message: () -> String) {
        if (delegate.isWarnEnabled) delegate.warn(message())
    }

    inline fun warn(throwable: Throwable, message: () -> String) {
        if (delegate.isWarnEnabled) delegate.warn(message(), throwable)
    }

    /**
     * Write [message] at ERROR, which is written synchronously on the caller's thread. See
     * `docs/standards/logging-standard.md` for what may be written at ERROR.
     */
    inline fun error(message: () -> String) {
        if (delegate.isErrorEnabled) delegate.error(message())
    }

    /**
     * Write [message] at ERROR with the [throwable] that caused it, if there is one.
     */
    inline fun error(throwable: Throwable?, message: () -> String) {
        if (delegate.isErrorEnabled) delegate.error(message(), throwable)
    }
}

/**
 * The [Logger] named for the class of the receiver.
 */
@Suppress("unused")
inline fun <reified T : Any> T.loggerForClass(): Logger = Logger(LoggerFactory.getLogger(T::class.java))
