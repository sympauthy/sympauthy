package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.config.model.AuthorizationFlowsConfig
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Manager providing methods shared between all types of end-user authorization flows.
 * This does not handle client authentication (e.g. client_credentials).
 */
@Singleton
class AuthorizationFlowManager(
    @Inject private val authorizationFlowsConfig: AuthorizationFlowsConfig,
) {

    /**
     * The interactive flow bundled with this authorization server.
     */
    val defaultInteractiveFlow: InteractiveFlow
        get() = authorizationFlowsConfig.orThrow().bundledFlow

    /**
     * Return the [AuthorizationFlow] identified by [id] or null.
     */
    fun findByIdOrNull(id: String): AuthorizationFlow? {
        return authorizationFlowsConfig.orThrow().flows
            .firstOrNull { it.id == id }
    }

    /**
     * Either return the completed [session] together with its non-null [oauth2] request record if the
     * session can be used to issue an access token to the [client], or throw one of the following
     * exceptions:
     * - [BusinessException] with "token.expired" if the session is missing, not an OAuth2 session, not
     *   completed, has expired, or has no attached [oauth2] request record.
     * - [BusinessException] with "token.mismatching_client" if the session was initiated by a different client.
     *
     * The [oauth2] record (fetched by the caller, nullable) is validated here so a session whose OAuth2
     * request record is missing cannot yield a token; it is returned non-null so the caller does not have
     * to re-check it.
     */
    suspend fun checkCanIssueToken(
        session: InteractiveFlowSession?,
        oauth2: InteractiveFlowSessionOAuth2?,
        client: Client
    ): Pair<CompletedInteractiveFlowSession, InteractiveFlowSessionOAuth2> {
        if (session !is CompletedInteractiveFlowSession) {
            throw businessExceptionOf("token.expired")
        }
        if (session.initiatingPurpose != InteractiveFlowPurpose.OAUTH2_AUTHORIZE) {
            throw businessExceptionOf("token.expired")
        }
        if (session.expired) {
            throw businessExceptionOf("token.expired")
        }
        if (oauth2 == null) {
            throw businessExceptionOf("token.expired")
        }
        if (oauth2.clientId != client.id) {
            throw businessExceptionOf("token.mismatching_client")
        }
        return session to oauth2
    }
}
