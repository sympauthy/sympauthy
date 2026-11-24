package com.sympauthy.api

/*
 * The route prefix of each surface. What each surface is, and how it is gated, is
 * `docs/design/architecture.md`.
 */

/** The OAuth 2.0 endpoints, named by their specifications. */
const val OAUTH2_SURFACE = "/api/oauth2"

/** The OpenID Connect endpoints, named by their specification. */
const val OPENID_SURFACE = "/api/openid"

/** The interactive flow the sign-in pages drive. */
const val FLOW_SURFACE = "/api/v1/flow"

/** What a client calls server-to-server with its own token. */
const val CLIENT_SURFACE = "/api/v1/client"

/** What a client calls on behalf of the person signed in to it, with an access token issued for them. */
const val USER_SURFACE = "/api/v1/user"

/** The back office the admin console drives. */
const val ADMIN_SURFACE = "/api/v1/admin"
