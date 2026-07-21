package com.sympauthy.it

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.util.JSONObjectUtils
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.sympauthy.testcontainers.SympauthyContainer
import com.sympauthy.testcontainers.flow.InteractiveFlowRegistry
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

/**
 * Shared support for the container-starting integration tests: a minimal SympAuthy configuration
 * (password auth + email identifier + a public client wired to the mock frontend), container
 * lifecycle with log capture on failure, and small HTTP/JWT helpers used by the scenarios.
 *
 * Each scenario boots a fresh container per [Database] so H2 and PostgreSQL are exercised identically.
 */
abstract class AbstractSympauthyIT {

    protected val clientId: String = "test-app"

    /**
     * Password auth with an email identifier, plus the public client the tests own — wired to the mock
     * frontend's callback and flow id. Only the `flows.<id>` definition is contributed by `withFlows`.
     */
    protected fun config(registry: InteractiveFlowRegistry): Map<String, Any> = mapOf(
        "auth" to mapOf(
            "by-password" to mapOf("enabled" to true),
            "identifier-claims" to listOf("email"),
        ),
        "claims" to mapOf("email" to mapOf("enabled" to true)),
        "clients" to mapOf(
            registry.clientId() to mapOf(
                "public" to true,
                "authorizationFlow" to registry.flowId(),
                "allowed-grant-types" to listOf("authorization_code"),
                "allowed-scopes" to listOf("openid"),
                "allowed-redirect-uris" to listOf(registry.redirectUri()),
            ),
        ),
    )

    /** A configured, not-yet-started container backed by [fixture] and wired to [registry]. */
    private fun newContainer(
        fixture: DatabaseFixture,
        registry: InteractiveFlowRegistry,
        extraConfig: Map<String, Any>,
    ): SympauthyContainer = fixture
        .applyTo(SympauthyContainer(SympauthyImage.resolve()).withConfig(deepMerge(config(registry), extraConfig)))
        .withFlows(registry)

    /**
     * Recursively merges [override] into [base]: nested maps are merged key-by-key, any other value
     * (scalar or list) replaces the base value. Lets a scenario contribute extra config — a second
     * client, extra scopes, a feature flag — on top of the shared [config] without restating it.
     */
    private fun deepMerge(base: Map<String, Any>, override: Map<String, Any>): Map<String, Any> {
        if (override.isEmpty()) return base
        val merged = LinkedHashMap<String, Any>(base)
        for ((key, overrideValue) in override) {
            val baseValue = merged[key]
            merged[key] = if (baseValue is Map<*, *> && overrideValue is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                deepMerge(baseValue as Map<String, Any>, overrideValue as Map<String, Any>)
            } else {
                overrideValue
            }
        }
        return merged
    }

    /**
     * Starts a SympAuthy container backed by [database] with the mock flow frontend attached, runs
     * [block] against it, and always tears both down. Dumps the container logs to stderr on failure to
     * make CI diagnostics actionable. Pass [extraConfig] to deep-merge scenario-specific configuration
     * (e.g. a second client) on top of the shared base [config].
     */
    protected fun withContainer(
        database: Database,
        extraConfig: Map<String, Any> = emptyMap(),
        block: (SympauthyContainer, InteractiveFlowRegistry) -> Unit,
    ) {
        database.createFixture().use { fixture ->
            InteractiveFlowRegistry.forClient(clientId).withScopes("openid").use { registry ->
                newContainer(fixture, registry, extraConfig).use { sympauthy ->
                    sympauthy.start()
                    try {
                        block(sympauthy, registry)
                    } catch (failure: Throwable) {
                        System.err.println("=== SYMPAUTHY CONTAINER LOGS ===")
                        System.err.println(runCatching { sympauthy.logs }.getOrElse { "(logs unavailable: $it)" })
                        throw failure
                    }
                }
            }
        }
    }

    // --- HTTP / JWT helpers -------------------------------------------------------------------------

    /** The parsed OpenID Connect discovery document served by [sympauthy]. */
    protected fun discovery(sympauthy: SympauthyContainer): Map<String, Any?> {
        val response = httpGet(sympauthy.openIdConfigurationUrl, followRedirects = true)
        check(response.statusCode() == 200) {
            "discovery returned HTTP ${response.statusCode()}: ${response.body()}"
        }
        return JSONObjectUtils.parse(response.body())
    }

    /**
     * Verifies [idToken] is genuinely signed by the key set [sympauthy] advertises at its `jwks_uri`,
     * returning the validated claims. Throws if the signature does not verify against the JWKS.
     */
    protected fun verifyIdTokenSignature(sympauthy: SympauthyContainer, idToken: String): JWTClaimsSet {
        val jwksUri = URI.create(discovery(sympauthy)["jwks_uri"] as String).toURL()
        val signedJwt = SignedJWT.parse(idToken)
        val jwkSource = JWKSourceBuilder.create<SecurityContext>(jwksUri).build()
        val processor = DefaultJWTProcessor<SecurityContext>().apply {
            jwsKeySelector = JWSVerificationKeySelector(signedJwt.header.algorithm, jwkSource)
        }
        return processor.process(signedJwt, null)
    }

    /**
     * Builds a valid authorization request URL for the mock frontend's public client (with a fresh
     * PKCE challenge). Pass [overrides] to replace individual query parameters for a specific scenario;
     * a null override value removes that parameter entirely (e.g. to omit PKCE and prove it is
     * mandatory).
     */
    protected fun authorizeUrl(
        sympauthy: SympauthyContainer,
        registry: InteractiveFlowRegistry,
        overrides: Map<String, String?> = emptyMap(),
    ): String {
        val params = linkedMapOf(
            "response_type" to "code",
            "client_id" to registry.clientId(),
            "redirect_uri" to registry.redirectUri(),
            "scope" to "openid",
            "state" to "integration-test-state",
            "code_challenge" to generatePkce().challenge,
            "code_challenge_method" to "S256",
        )
        overrides.forEach { (key, value) -> if (value == null) params.remove(key) else params[key] = value }
        val query = params.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        return "${discovery(sympauthy)["authorization_endpoint"]}?$query"
    }

    protected fun httpGet(
        url: String,
        followRedirects: Boolean = false,
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse<String> =
        client(followRedirects).send(
            HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    protected fun httpPostForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse<String> =
        client(followRedirects = false).send(
            HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(form)))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    /** The `Authorization: Basic` header value for authenticating a confidential client. */
    protected fun basicAuth(clientId: String, secret: String): String =
        "Basic " + Base64.getEncoder()
            .encodeToString("$clientId:$secret".toByteArray(StandardCharsets.UTF_8))

    /**
     * Builds a DPoP proof JWT (RFC 9449) signed with a fresh ES256 key whose public JWK is embedded in
     * the header. The defaults produce a *valid* proof for a `POST` to [htu]; override [htm] or [iat] to
     * forge an invalid one (wrong HTTP method, stale timestamp) for negative tests.
     */
    protected fun dpopProof(
        htu: String,
        htm: String = "POST",
        iat: Instant = Instant.now(),
    ): String {
        val ecKey = ECKeyGenerator(Curve.P_256).generate()
        val header = JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(JOSEObjectType("dpop+jwt"))
            .jwk(ecKey.toPublicJWK())
            .build()
        val claims = JWTClaimsSet.Builder()
            .jwtID(UUID.randomUUID().toString())
            .claim("htm", htm)
            .claim("htu", htu)
            .issueTime(Date.from(iat))
            .build()
        return SignedJWT(header, claims).apply { sign(ECDSASigner(ecKey)) }.serialize()
    }

    /** The request header map carrying a DPoP [proof]. */
    protected fun dpopHeader(proof: String): Map<String, String> = mapOf("DPoP" to proof)

    private fun client(followRedirects: Boolean): HttpClient = HttpClient.newBuilder()
        .followRedirects(if (followRedirects) HttpClient.Redirect.NORMAL else HttpClient.Redirect.NEVER)
        .build()

    protected fun formEncode(form: Map<String, String>): String =
        form.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }

    protected fun encode(value: String): String =
        java.net.URLEncoder.encode(value, StandardCharsets.UTF_8)

    /** A PKCE verifier/challenge pair (S256), matching what a public client must send. */
    protected fun generatePkce(): Pkce {
        val verifier = base64Url(ByteArray(32).also { SecureRandom().nextBytes(it) })
        val challenge = base64Url(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII)),
        )
        return Pkce(verifier, challenge)
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /** A PKCE verifier and its S256 challenge. */
    protected data class Pkce(val verifier: String, val challenge: String, val method: String = "S256")
}
