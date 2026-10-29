package com.sympauthy.exception

import com.sympauthy.api.exception.httpExceptionOf
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.monitoring.telemetry.Outcome
import io.micronaut.http.HttpStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExceptionOutcomeTest {

    @Test
    fun `toOutcome - Is a refusal typed by its code for a protocol error`() {
        assertEquals(
            Outcome.Refused("code.already_exchanged"),
            oauth2ExceptionOf(INVALID_GRANT, "code.already_exchanged").toOutcome()
        )
    }

    @Test
    fun `toOutcome - Is a refusal for a business failure answering a 4xx, recoverable or not`() {
        assertEquals(
            Outcome.Refused("flow.password.sign_in.invalid"),
            recoverableBusinessExceptionOf("flow.password.sign_in.invalid", "description").toOutcome()
        )
        assertEquals(Outcome.Refused("grant.revoked"), businessExceptionOf("grant.revoked").toOutcome())
    }

    @Test
    fun `toOutcome - Is a refusal for a recoverable business failure that recommends no status`() {
        val failure = BusinessException(recoverable = true, detailsId = "flow.sign_up.disabled")

        assertEquals(Outcome.Refused("flow.sign_up.disabled"), failure.toOutcome())
    }

    @Test
    fun `toOutcome - Is an error for a failure of the server`() {
        assertEquals(Outcome.Error("code.missing_user"), internalBusinessExceptionOf("code.missing_user").toOutcome())
        assertEquals(
            Outcome.Error("internal_server_error"),
            httpExceptionOf(HttpStatus.INTERNAL_SERVER_ERROR, "internal_server_error", "description").toOutcome()
        )
    }

    @Test
    fun `toOutcome - Is an error typed by its class for a failure that carries no code`() {
        assertEquals(Outcome.Error("java.lang.IllegalStateException"), IllegalStateException("boom").toOutcome())
    }
}
