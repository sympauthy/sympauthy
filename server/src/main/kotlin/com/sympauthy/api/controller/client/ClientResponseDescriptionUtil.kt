package com.sympauthy.api.controller.client

import com.sympauthy.api.util.UNAUTHORIZED_RESPONSE_DESCRIPTION

/**
 * What the published specification says about the `401` of an operation whose body carries an end-user's
 * `access_token` beside the client's own: which of the two tokens the status is about.
 */
const val CLIENT_TOKEN_UNAUTHORIZED_RESPONSE_DESCRIPTION = "The access token is this client's own, in the " +
    "`Authorization` header, and not the `access_token` of the body.\n\n" + UNAUTHORIZED_RESPONSE_DESCRIPTION

/**
 * What the published specification says about the `404` of every operation naming a user by its
 * identifier on the client surface.
 */
const val CLIENT_USER_NOT_FOUND_RESPONSE_DESCRIPTION =
    "- `not_found`: no user with this identifier has an active consent for this client's audience."
