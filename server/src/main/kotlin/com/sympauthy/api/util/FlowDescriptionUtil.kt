package com.sympauthy.api.util

/**
 * The `401` of a flow operation reading the `state` from the query string, which is how
 * `StateAuthenticationFetcher` reads it on a `GET`.
 *
 * The constants of this file are what the published specification says about the flow surface, on every
 * operation or resource of it the sentence is true of. A flow operation resolving an interactive flow
 * session goes through `InteractiveAuthFlowSessionControllerUtil`, so its refusals of the `state` and what
 * becomes of a failure the end-user cannot correct are that helper's, and written once here rather than per
 * controller.
 */
const val FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION =
    "- `unauthorized`: the request carries no `state` query parameter, or an empty one."

/**
 * The `401` of a flow operation reading the `state` from the `Authorization` header, which is how
 * `StateAuthenticationFetcher` reads it on a `POST`.
 */
const val FLOW_HEADER_STATE_UNAUTHORIZED_DESCRIPTION =
    "- `unauthorized`: the request carries no `Authorization: State <state>` header."

/**
 * The lines of the `400` every flow operation resolving an interactive flow session answers with when its
 * `state` resolves to none. `missing_state` is left out: an operation gated by the `state` answers `401`
 * before it is read.
 */
const val FLOW_STATE_BAD_REQUEST_DESCRIPTION =
    "- `auth.interactive_flow_session.validate.wrong_signature`: the `state` is malformed or was not " +
        "signed by this server.\n" +
        "- `auth.interactive_flow_session.validate.missing_session`: the interactive flow session the " +
        "`state` names has expired and been deleted."

/**
 * The line a flow operation appends to [FLOW_STATE_BAD_REQUEST_DESCRIPTION] when it answers with a body of
 * its own rather than a redirect, and so refuses a session that is over instead of sending the browser where
 * the session stands.
 */
const val FLOW_ONGOING_SESSION_BAD_REQUEST_DESCRIPTION =
    "\n- `ctrl.flow.not_ongoing`: the interactive flow session has already completed, failed or been " +
        "cancelled."

/**
 * The lines such an operation appends instead when it also needs the session's user, and so refuses a
 * session that has no user yet as well.
 */
const val FLOW_SIGNED_IN_SESSION_BAD_REQUEST_DESCRIPTION = FLOW_ONGOING_SESSION_BAD_REQUEST_DESCRIPTION +
    "\n- `auth.interactive_flow_session.user.missing`: the end-user has not signed in to the interactive " +
    "flow session yet."

/**
 * What becomes of a failure the end-user cannot correct, on a flow operation answering with a
 * `redirect_url`.
 */
const val FLOW_FAILURE_DESCRIPTION = "A failure the end-user cannot correct fails the interactive flow " +
    "session, and `redirect_url` then leads to the error page."

/**
 * What becomes of a failure the end-user cannot correct, on a flow operation answering with a redirect.
 */
const val FLOW_FAILURE_SEE_OTHER_DESCRIPTION = "A failure the end-user cannot correct fails the interactive " +
    "flow session and redirects the browser to the error page."

/**
 * The success response of a flow operation answering with nothing but a `redirect_url`.
 */
const val FLOW_REDIRECT_RESPONSE_DESCRIPTION = "The `redirect_url` to send the browser to next."

/**
 * The `303` of a flow operation that redirects the browser to wherever the interactive flow session
 * continues.
 */
const val FLOW_SEE_OTHER_DESCRIPTION = "Redirects the browser to the next step of the flow, to its error " +
    "page, or, once the interactive flow session has ended, to the URL it returns the end-user to."

/**
 * The `redirect_url` of a response that always carries one.
 */
const val FLOW_REDIRECT_URL_DESCRIPTION = "URL to send the browser to next: a step of the flow, its error " +
    "page, or, once the interactive flow session has ended, the URL it returns the end-user to."

/**
 * The `redirect_url` a step's response carries in place of the step. Each step appends when it is absent.
 */
const val STEP_REDIRECT_URL_DESCRIPTION = "URL to send the browser to instead of rendering this step: " +
    "another step of the flow, its error page, or, once the interactive flow session has ended, the URL it " +
    "returns the end-user to."

/**
 * What a step's response says of the URLs it holds.
 */
const val STEP_URL_STATE_DESCRIPTION = "Every URL it holds that leads back into the flow already carries " +
    "the `state` query parameter."
