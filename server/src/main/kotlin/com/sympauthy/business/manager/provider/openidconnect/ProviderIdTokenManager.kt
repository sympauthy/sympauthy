package com.sympauthy.business.manager.provider.openidconnect

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.util.DefaultResourceRetriever
import com.nimbusds.jose.util.ResourceRetriever
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.model.jwt.HashAlgorithm
import com.sympauthy.business.model.provider.config.ProviderOpenIdConnectConfig
import com.sympauthy.client.remoteOutcome
import com.sympauthy.monitoring.Condition
import com.sympauthy.monitoring.RemoteCallMonitor
import com.sympauthy.monitoring.telemetry.LibraryHttpCallTracer
import com.sympauthy.monitoring.telemetry.Outcome
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Validates and extracts claims from ID tokens returned by OpenID Connect providers.
 *
 * Verification includes signature validation against the provider's JWKS, and standard claim checks:
 * issuer, audience, expiration, and nonce (if provided).
 *
 * The check itself is local. Only the fetch of the provider's key set leaves the process, and it is reported to
 * the provider's condition as a call of its own, and traced as one: the JWT library makes it outside the HTTP
 * client the framework instruments, so nothing else would show it.
 */
@Singleton
class ProviderIdTokenManager(
    @Inject private val remoteCallMonitor: RemoteCallMonitor,
    @Inject private val libraryHttpCallTracer: LibraryHttpCallTracer
) {

    /**
     * Verify [idTokenRaw] against [openIdConnectConfig] — its signature against the JWKS of the provider [providerId]
     * names, then its issuer, audience and expiry — and return the claims it carries.
     *
     * [expectedNonce] is matched against the token's `nonce` claim, and passing one also makes that claim
     * required rather than optional. [accessToken] is the one from the same token response, and is read only to
     * check the `at_hash` claim when the provider sent one.
     *
     * Anything the token fails on throws a business exception carrying
     * `provider.openid_connect.invalid_id_token`, except a mismatching `at_hash`, which has its own
     * `provider.openid_connect.invalid_at_hash`.
     */
    suspend fun validateAndExtractClaims(
        providerId: String,
        openIdConnectConfig: ProviderOpenIdConnectConfig,
        idTokenRaw: String,
        accessToken: String,
        expectedNonce: String?
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            val jwkSource = JWKSourceBuilder
                .create<SecurityContext>(openIdConnectConfig.jwksUri.toURL(), keySetRetriever(providerId))
                .build()

            val jwtProcessor = DefaultJWTProcessor<SecurityContext>()
            jwtProcessor.jwsKeySelector = JWSVerificationKeySelector(
                SUPPORTED_ALGORITHMS,
                jwkSource
            )

            val requiredClaims = mutableSetOf("sub", "iss", "aud", "exp", "iat")
            if (expectedNonce != null) {
                requiredClaims.add("nonce")
            }
            val exactMatchClaims = JWTClaimsSet.Builder()
                .issuer(openIdConnectConfig.issuer.toString())
                .apply {
                    if (expectedNonce != null) {
                        claim("nonce", expectedNonce)
                    }
                }
                .build()

            jwtProcessor.jwtClaimsSetVerifier = DefaultJWTClaimsVerifier<SecurityContext>(
                setOf(openIdConnectConfig.clientId),
                exactMatchClaims,
                requiredClaims,
                emptySet()
            )

            val claimsSet = jwtProcessor.process(idTokenRaw, null)

            // Validate at_hash if present (OIDC Core §3.1.3.8)
            val atHash = claimsSet.getStringClaim("at_hash")
            if (atHash != null) {
                val signedJwt = SignedJWT.parse(idTokenRaw)
                val alg = signedJwt.header.algorithm
                validateAtHash(atHash, accessToken, alg, openIdConnectConfig.clientId)
            }

            claimsSet.claims
                .filterValues { it != null }
                .mapValues { it.value!! }
        } catch (e: com.sympauthy.business.exception.BusinessException) {
            throw e
        } catch (e: Exception) {
            throw businessExceptionOf(
                "provider.openid_connect.invalid_id_token",
                "providerId" to openIdConnectConfig.clientId,
                "reason" to (e.message ?: "unknown"),
                throwable = e
            )
        }
    }

    /**
     * Check that [atHash] is the `at_hash` of [accessToken] under [alg], the algorithm the id token carrying it
     * was signed with (OIDC Core §3.1.3.8), and throw `provider.openid_connect.invalid_at_hash` naming
     * [providerId] where it is not.
     *
     * An [alg] naming a digest [HashAlgorithm] does not know is one the provider chose and this server cannot
     * reproduce, so the claim goes unchecked rather than refused.
     */
    internal fun validateAtHash(atHash: String, accessToken: String, alg: JWSAlgorithm, providerId: String) {
        val hashAlgorithm = HashAlgorithm.ofOrNull(alg) ?: return
        if (atHash != hashAlgorithm.atHash(accessToken)) {
            throw businessExceptionOf(
                "provider.openid_connect.invalid_at_hash",
                "providerId" to providerId
            )
        }
    }

    /**
     * What fetches the key set of the provider [providerId] names for the JWT library, reporting each fetch as a
     * call to that provider's condition and tracing it as a client span under the request validating the id token.
     */
    private fun keySetRetriever(providerId: String): ResourceRetriever {
        val condition = Condition.Provider(providerId)
        return ResourceRetriever { url ->
            val delegate = StatusKeepingResourceRetriever()
            libraryHttpCallTracer.get(url, delegate::responseStatusOrNull) {
                val start = System.nanoTime()
                val resource = try {
                    delegate.retrieveResource(url)
                } catch (failure: Exception) {
                    val outcome = failure.remoteOutcome()
                    remoteCallMonitor.reportCall(condition, JWKS_OPERATION, System.nanoTime() - start, outcome, failure)
                    throw failure
                }
                remoteCallMonitor.reportCall(condition, JWKS_OPERATION, System.nanoTime() - start, Outcome.Success)
                resource
            }
        }
    }

    /**
     * The JWT library's own retriever, keeping the connection of the one fetch it is made for so that the status
     * the provider answered with can be read once the fetch has ended, which the library reports only in a message.
     */
    private class StatusKeepingResourceRetriever : DefaultResourceRetriever(
        JWKSourceBuilder.DEFAULT_HTTP_CONNECT_TIMEOUT,
        JWKSourceBuilder.DEFAULT_HTTP_READ_TIMEOUT,
        JWKSourceBuilder.DEFAULT_HTTP_SIZE_LIMIT
    ) {
        private var connection: HttpURLConnection? = null

        override fun openHTTPConnection(url: URL): HttpURLConnection =
            super.openHTTPConnection(url).also { connection = it }

        /**
         * The status the provider answered with, or null where it answered none.
         *
         * Reading it after a fetch that could not connect does not connect again: the connection keeps the failure
         * and throws it back.
         */
        fun responseStatusOrNull(): Int? = connection
            ?.let { runCatching { it.responseCode }.getOrNull() }
            ?.takeIf { it > 0 }
    }

    companion object {
        /**
         * The operation the fetch of a provider's key set is timed under.
         */
        private const val JWKS_OPERATION = "jwks"

        private val SUPPORTED_ALGORITHMS = setOf(
            JWSAlgorithm.RS256,
            JWSAlgorithm.RS384,
            JWSAlgorithm.RS512,
            JWSAlgorithm.ES256,
            JWSAlgorithm.ES384,
            JWSAlgorithm.ES512,
            JWSAlgorithm.PS256,
            JWSAlgorithm.PS384,
            JWSAlgorithm.PS512
        )
    }
}
