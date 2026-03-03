package com.sympauthy.business.model.flow

import java.net.URI

sealed class AuthorizationFlow(
    val id: String
) {
    companion object {
        const val DEFAULT_WEB_AUTHORIZATION_FLOW_ID = "default_web"
    }
}

/**
 * Represents a non-interactive authorization flow.
 *
 * This class is a specific type of `AuthorizationFlow` designed for scenarios
 * where no user interaction is required during the authorization process.
 * It inherits common properties and behavior from the `AuthorizationFlow` base class.
 */
class NonInteractiveAuthorizationFlow(id: String) : AuthorizationFlow(id)

/**
 * Represents an interactive authorization flow where the end-users are redirected to web pages, each handling a
 * specific step of the authorization flow.
 *
 * Contains where the end-user must be redirected to go through the different step of the authorization flow.
 */
class WebAuthorizationFlow(
    id: String,
    /**
     * [URI] of the page allowing the user either to:
     * - authenticate by entering its credentials
     * - select a third-party provider that will authenticate him.
     */
    val signInUri: URI,
    /**
     * [URI] of the page in charge of collecting claims from the end-user claims.
     *
     * This page will be presented during the authentication flow whenever all required claims
     * are not collected for an end-user.
     */
    val collectClaimsUri: URI,
    /**
     * [URI] of the page in charge of collecting validation code from the user to validate claims that
     * requires to be verified (ex. email).
     * The **media** query param will contain the media with which a validation must be performed (ex. EMAIL).
     *
     * The authorization flow may go through this page multiple times with different media.
     * This page will be skipped if none of the collected claims require validation.
     */
    val validateClaimsUri: URI,
    /**
     * [URI] of the page displaying an error to the end-user.
     */
    val errorUri: URI,
    /**
     * [URI] of the MFA router page.
     * The end-user is redirected here when MFA is required. That page calls GET /api/v1/flow/mfa
     * to determine which MFA sub-step to present next (enrollment or challenge).
     * Null if MFA is not configured for this flow.
     */
    val mfaUri: URI? = null,
    /**
     * [URI] of the page allowing the user to enroll a TOTP authenticator app.
     * Null if MFA via TOTP is not configured for this flow.
     */
    val mfaTotpEnrollUri: URI? = null,
    /**
     * [URI] of the page allowing the user to complete the TOTP challenge.
     * Null if MFA via TOTP is not configured for this flow.
     */
    val mfaTotpChallengeUri: URI? = null,
) : AuthorizationFlow(
    id = id
)

enum class AuthorizationFlowType {
    WEB
}
