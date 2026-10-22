package com.sympauthy.it.compliance

import com.sympauthy.it.AbstractSympauthyIT
import com.sympauthy.it.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Compliance scenario — **the token, introspection and revocation endpoints answer a request the server
 * refuses before reading it in the body RFC 6749 defines, with `invalid_request`.**
 *
 * A client library reads `error` off every failure of these endpoints, so a refusal answered in this
 * server's own body tells it nothing. This posts JSON to each endpoint and sends it a `GET`, and expects
 * the `415` and the `405` those requests are due, each naming `invalid_request`, and the `405` with the
 * `Allow` header naming `POST`, on each supported database.
 *
 * Source: [RFC 6749 §5.2 (Error Response)](https://datatracker.ietf.org/doc/html/rfc6749#section-5.2),
 * [RFC 7662 §2.3](https://datatracker.ietf.org/doc/html/rfc7662#section-2.3),
 * [RFC 7009 §2.2.1](https://datatracker.ietf.org/doc/html/rfc7009#section-2.2.1).
 */
@Tag("compliance")
class ProtocolEndpointErrorBodyIT : AbstractSympauthyIT() {

    @ParameterizedTest(name = "a request refused before an endpoint reads it is an OAuth2 error on {0}")
    @EnumSource(Database::class)
    fun refusedRequestIsAnOAuth2Error(database: Database) {
        withContainer(database) { sympauthy, _ ->
            val discovery = discovery(sympauthy)
            val endpoints = listOf(
                discovery.tokenEndpoint,
                discovery.introspectionEndpoint!!,
                discovery.revocationEndpoint!!,
            )
            endpoints.forEach { endpoint ->
                assertOAuthError(
                    httpPost(endpoint, "application/json", """{"grant_type":"client_credentials"}"""),
                    415, "invalid_request",
                    "a JSON body posted to $endpoint must be refused as an OAuth2 error",
                )

                val wrongMethod = httpGet(endpoint)
                assertOAuthError(
                    wrongMethod, 405, "invalid_request",
                    "a GET to $endpoint must be refused as an OAuth2 error",
                )
                assertEquals(
                    "POST",
                    wrongMethod.headers().firstValue("Allow").orElse(null),
                    "a GET to $endpoint must name the method it answers",
                )
            }
        }
    }
}
