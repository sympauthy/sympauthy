package com.sympauthy.it.security

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Security scenario — **the authorization endpoint must redirect with `303 See Other`, never a
 * `307 Temporary Redirect`.**
 *
 * Risk: OAuth 2.1 forbids 307 on the authorization endpoint because a 307 preserves the original HTTP
 * method and body, so a browser would **re-submit the POST body — including credentials — to the
 * redirect target**. A 303 forces the user-agent to follow up with a GET, dropping the body. Regressing
 * this (e.g. returning 302/307) would leak credentials to downstream URLs.
 *
 * This verifies a valid authorize request on the running native image is answered with a 303, on each
 * supported database.
 *
 * Source: [OAuth 2.1, HTTP 307 Redirect](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-1#name-http-307-redirect).
 */
@Tag("security")
class Authorize303SeeOtherIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "authorize redirects with 303 See Other (never 307) on {0}")
    @EnumSource(Database::class)
    fun authorizeRedirectsWith303SeeOther(database: Database) {
        withContainer(database) { sympauthy, registry ->
            val response = httpGet(authorizeUrl(sympauthy, registry), followRedirects = false)

            assertEquals(
                303,
                response.statusCode(),
                "authorize must redirect with 303 See Other (OAuth 2.1 forbids 307), was ${response.statusCode()}",
            )
            assertNotEquals(307, response.statusCode(), "authorize must never use a 307 Temporary Redirect")
        }
    }
}
