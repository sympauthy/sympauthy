package com.sympauthy.it

import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jose.util.JSONObjectUtils
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.sympauthy.api.client.api.OpeniddiscoveryApi
import com.sympauthy.api.client.model.OpenIdConfigurationResource
import com.sympauthy.it.client.BearerTokenHolder
import com.sympauthy.it.client.FlowCall
import com.sympauthy.it.client.FlowHttpClient
import com.sympauthy.testcontainers.Client
import com.sympauthy.testcontainers.SympauthyContainer
import com.sympauthy.testcontainers.client.TokenClient
import com.sympauthy.testcontainers.client.TokenResponse
import com.sympauthy.testcontainers.flow.AuthorizationResult
import com.sympauthy.testcontainers.flow.Credentials
import com.sympauthy.testcontainers.flow.InteractiveFlowRegistry
import io.micronaut.context.ApplicationContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Shared support for the container-starting integration tests: a minimal SympAuthy configuration
 * (password auth + email identifier + a public client wired to the mock frontend), container
 * lifecycle with log capture on failure, and small HTTP/JWT helpers used by the scenarios.
 *
 * Each scenario boots a fresh container per [Database] so H2 and PostgreSQL are exercised identically.
 */
abstract class AbstractSympauthyIT {

    protected val clientId: String = "test-app"

    // --- Configuration fixtures ---------------------------------------------------------------------

    /**
     * Password auth with an email identifier, which every scenario's deployment is built on: it is what
     * makes a sign-up and a sign-in possible at all.
     *
     * Compose anything else onto it with [and] rather than with `+`: a scenario adding, say,
     * `auth.token.refresh-enabled` with `plus` replaces the whole `auth` subtree and silently takes the
     * password authentication with it.
     */
    protected fun passwordAuthConfig(): Map<String, Any> = mapOf(
        "auth" to mapOf(
            "by-password" to mapOf("enabled" to true),
            "identifier-claims" to listOf("email"),
        ),
        "claims" to mapOf("email" to mapOf("enabled" to true)),
    )

    /**
     * This configuration with [override] merged into it subtree by subtree: a map meets the map under the
     * same key, and any other value replaces what was there. It is what composing two configuration
     * fragments means, since the ones here share the `auth`, `claims` and `clients` keys and each
     * contributes different members of them.
     */
    protected infix fun Map<String, Any>.and(override: Map<String, Any>): Map<String, Any> =
        deepMerge(this, override)

    /**
     * A public client owning [registry]'s flow — wired to the mock frontend's callback and flow id, and
     * allowed [scopes]. [defaultScopes] is what the client asks for when a request names no scope, and it
     * has to sit inside [scopes] or the server refuses to boot.
     */
    protected fun publicClientConfig(
        registry: InteractiveFlowRegistry,
        scopes: List<String> = listOf("openid"),
        defaultScopes: List<String>? = null,
    ): Map<String, Any> = mapOf(
        "public" to true,
        "authorizationFlow" to registry.flowId(),
        "allowed-grant-types" to listOf("authorization_code"),
        "allowed-scopes" to scopes,
        "allowed-redirect-uris" to listOf(registry.redirectUri()),
    ) + (defaultScopes?.let { mapOf("default-scopes" to it) } ?: emptyMap())

    /**
     * Password auth with an email identifier, plus the client the tests own — wired to the mock
     * frontend's callback and flow id. The client is public or confidential according to
     * [InteractiveFlowRegistry.client]; a confidential client also carries its secret and the
     * `refresh_token` grant. Only the `flows.<id>` definition is contributed by `withFlows`.
     */
    protected fun config(registry: InteractiveFlowRegistry): Map<String, Any> {
        val clientConfig = if (registry.client().isPublic) {
            publicClientConfig(registry, defaultScopes = listOf("openid"))
        } else {
            mapOf(
                "public" to false,
                "secret" to registry.clientSecret(),
                "authorizationFlow" to registry.flowId(),
                "allowed-grant-types" to listOf("authorization_code", "refresh_token"),
                "allowed-scopes" to listOf("openid"),
                "default-scopes" to listOf("openid"),
                "allowed-redirect-uris" to listOf(registry.redirectUri()),
            )
        }
        return passwordAuthConfig() and mapOf("clients" to mapOf(registry.clientId() to clientConfig))
    }

    // --- On-demand MFA-enrollment support ----------------------------------------------------------

    /** The `return_uri` a standalone MFA-enrollment flow redirects the end-user to on success. */
    protected fun mfaEnrollmentReturnUri(registry: InteractiveFlowRegistry): String =
        "${registry.frontendUrl()}/mfa-return"

    /** The `cancel_uri` a standalone MFA-enrollment flow redirects the end-user to on cancellation. */
    protected fun mfaEnrollmentCancelUri(registry: InteractiveFlowRegistry): String =
        "${registry.frontendUrl()}/mfa-cancel"

    /**
     * The full server configuration for the on-demand MFA-enrollment client API: password auth, a
     * confidential client that owns [registry]'s flow and is allowed both the `authorization_code` grant
     * (to obtain an end-user access token) and the `client_credentials` grant carrying `users:mfa:write`
     * (to call the enrollment endpoint), with the return/cancel URIs registered as redirect URIs.
     *
     * `features.grant-unhandled-scopes` lets the `client_credentials` grant actually hand out
     * `users:mfa:write` (there is no dedicated scope-granting rule), and `templates.clients.default` points
     * the standalone flow's pages at the mock frontend rather than the built-in flow pages under `<root>/flow`.
     */
    protected fun mfaEnrollmentConfig(registry: InteractiveFlowRegistry): Map<String, Any> {
        val secret = registry.clientSecret()
            ?: error("on-demand MFA enrollment requires a confidential client (client_credentials grant)")
        return passwordAuthConfig() and mapOf(
            "features" to mapOf("grant-unhandled-scopes" to true),
            "templates" to mapOf(
                "clients" to mapOf("default" to mapOf("authorization-flow" to registry.flowId())),
            ),
            "clients" to mapOf(
                registry.clientId() to mapOf(
                    "secret" to secret,
                    "authorizationFlow" to registry.flowId(),
                    "allowed-grant-types" to listOf("authorization_code", "client_credentials"),
                    "allowed-scopes" to listOf("openid", "users:mfa:write"),
                    "default-scopes" to listOf("openid"),
                    "allowed-redirect-uris" to listOf(
                        registry.redirectUri(),
                        mfaEnrollmentReturnUri(registry),
                        mfaEnrollmentCancelUri(registry),
                    ),
                ),
            ),
            "mfa" to mapOf("required" to false, "totp" to mapOf("enabled" to true)),
        )
    }

    /**
     * A configured, not-yet-started container backed by [fixture], running [config] and serving the mock
     * frontend of each of [registries] — one `withFlows` apiece, so a scenario driving two clients passes
     * both and each resolves its own pages.
     */
    protected fun container(
        fixture: DatabaseFixture,
        config: Map<String, Any>,
        vararg registries: InteractiveFlowRegistry,
    ): SympauthyContainer = registries.fold(
        fixture.applyTo(SympauthyContainer(SympauthyImage.resolve()).withConfig(config)),
    ) { container, registry -> container.withFlows(registry) }

    /** A configured, not-yet-started container backed by [fixture] and wired to [registry]. */
    private fun newContainer(
        fixture: DatabaseFixture,
        registry: InteractiveFlowRegistry,
        extraConfig: Map<String, Any>,
    ): SympauthyContainer = container(fixture, deepMerge(config(registry), extraConfig), registry)

    /**
     * Recursively merges [override] into [base]: nested maps are merged key-by-key, any other value
     * (scalar or list) replaces the base value. Lets a scenario contribute extra config — a second
     * client, extra scopes, a feature flag — on top of the shared [config] without restating it.
     */
    protected fun deepMerge(base: Map<String, Any>, override: Map<String, Any>): Map<String, Any> {
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
     * (e.g. a second client) on top of the shared base [config], and [client] to own the flow as a
     * confidential client (default: a public client using PKCE). Pass [scopes] to drive the flow as a
     * plain OAuth 2.0 client, asking for something other than `openid`; the base [config] allows that
     * client `openid` alone and defaults it to the same, so a scenario overriding this overrides both
     * `allowed-scopes` and `default-scopes` with it — a default outside the allowed set is refused at
     * boot.
     */
    protected fun withContainer(
        database: Database,
        extraConfig: Map<String, Any> = emptyMap(),
        client: Client = Client.publicClient(clientId),
        scopes: List<String> = listOf("openid"),
        block: (SympauthyContainer, InteractiveFlowRegistry) -> Unit,
    ) {
        database.createFixture().use { fixture ->
            InteractiveFlowRegistry.forClient(client).withScopes(*scopes.toTypedArray()).use { registry ->
                newContainer(fixture, registry, extraConfig).use { sympauthy ->
                    runStarted(sympauthy, registry, block)
                }
            }
        }
    }

    /**
     * Starts a caller-configured container against [database], runs [block], and always tears down —
     * dumping the container logs on failure like [withContainer]. Use this when a scenario must configure
     * the container itself (the admin environment, an admin client, bootstrap invitations, a non-default
     * claim set) rather than the shared [config]/public-client setup [withContainer] provides. [build]
     * receives the created [DatabaseFixture] and the registry (whose client is [client] and whose authorize
     * request asks for [scopes]) and must return the fully configured, not-yet-started container —
     * typically [container]. A `withAdminClient` inside [build] sets the scopes itself, which then win.
     */
    protected fun withCustomContainer(
        database: Database,
        client: Client = Client.publicClient(clientId),
        scopes: List<String> = listOf("openid"),
        flowId: String? = null,
        build: (DatabaseFixture, InteractiveFlowRegistry) -> SympauthyContainer,
        block: (SympauthyContainer, InteractiveFlowRegistry) -> Unit,
    ) {
        database.createFixture().use { fixture ->
            InteractiveFlowRegistry.forClient(client)
                .withScopes(*scopes.toTypedArray())
                .apply { flowId?.let { withFlowId(it) } }
                .use { registry ->
                build(fixture, registry).use { sympauthy ->
                    runStarted(sympauthy, registry, block)
                }
            }
        }
    }

    /** Starts [sympauthy], runs [block], and dumps the container logs to stderr on any failure. */
    private fun runStarted(
        sympauthy: SympauthyContainer,
        registry: InteractiveFlowRegistry,
        block: (SympauthyContainer, InteractiveFlowRegistry) -> Unit,
    ) = withStartedContainer(sympauthy) { block(it, registry) }

    /**
     * Starts [sympauthy] (already fully configured and wired to any number of registries), runs [block],
     * and dumps the container logs to stderr on any failure. The caller owns the container and registry
     * lifecycles (typically via `.use {}`). Use this for scenarios that need more than the single registry
     * [withCustomContainer] manages — e.g. a second client with its own mock frontend.
     */
    protected fun withStartedContainer(sympauthy: SympauthyContainer, block: (SympauthyContainer) -> Unit) {
        sympauthy.start()
        try {
            block(sympauthy)
        } catch (failure: Throwable) {
            System.err.println("=== SYMPAUTHY CONTAINER LOGS ===")
            System.err.println(runCatching { sympauthy.logs }.getOrElse { "(logs unavailable: $it)" })
            throw failure
        }
    }

    // --- Generated OpenAPI client -------------------------------------------------------------------

    /**
     * Runs [block] with a Micronaut [ApplicationContext] hosting the OpenAPI client generated from the
     * server's contract, wired to the running [sympauthy] container (the `@Client("sympauthy")` service
     * URL is bound to the container's mapped address). When [token] is non-null it is attached as a bearer
     * token to every client request (see `BearerTokenClientFilter`). The context is closed on exit.
     *
     * Obtain the typed API beans inside the block, e.g. `ctx.getBean(AdminApi::class.java)`; their methods
     * return `reactor.core.publisher.Mono`, so call `.block()` to get the deserialized resource.
     */
    protected fun <T> withApiClient(
        sympauthy: SympauthyContainer,
        token: String? = null,
        block: (ApplicationContext) -> T,
    ): T = ApplicationContext.run(
        mapOf("micronaut.http.services.sympauthy.url" to sympauthy.baseUrl),
    ).use { ctx ->
        ctx.getBean(BearerTokenHolder::class.java).token = token
        block(ctx)
    }

    /**
     * Runs [block] with a [FlowHttpClient] bound to [sympauthy] that does **not** follow redirects, so a
     * scenario can read the `/authorize` `303` `Location` (which carries the signed internal state) and
     * drive the state-secured flow endpoints (`/api/v1/flow/cancel`). The hosting context is closed on exit.
     */
    protected fun <T> withFlowClient(
        sympauthy: SympauthyContainer,
        block: (FlowHttpClient) -> T,
    ): T = ApplicationContext.run(
        mapOf(
            "micronaut.http.services.sympauthy.url" to sympauthy.baseUrl,
            "micronaut.http.services.sympauthy.follow-redirects" to false,
        ),
    ).use { ctx -> block(ctx.getBean(FlowHttpClient::class.java)) }

    // --- Driving a flow -----------------------------------------------------------------------------

    /**
     * The values a password sign-up posts: the email identifier and the password, as the sign-up step takes
     * them. A scenario with nothing to prove about either takes both defaults; one proving something about
     * an address or a password names it.
     */
    protected fun credentials(email: String = DEFAULT_EMAIL, password: String = DEFAULT_PASSWORD): Map<String, String> =
        mapOf("email" to email, "password" to password)

    /** Signs a person up through [registry]'s flow with [credentials], and answers the authorization. */
    protected fun signUp(
        registry: InteractiveFlowRegistry,
        email: String = DEFAULT_EMAIL,
        password: String = DEFAULT_PASSWORD,
    ): AuthorizationResult = registry.newFlow()
        .withSignUpHandler { credentials(email, password) }
        .run()

    /** Signs a person up as [signUp] does, and exchanges the code it yields for tokens. */
    protected fun signUpAndExchange(
        registry: InteractiveFlowRegistry,
        email: String = DEFAULT_EMAIL,
        password: String = DEFAULT_PASSWORD,
    ): TokenResponse = signUp(registry, email, password).exchange()

    /**
     * The authorization code a sign-up through [registry]'s flow yields, for a scenario that will present
     * it at the token endpoint itself rather than through the driver's own exchange.
     */
    protected fun signUpForCode(
        registry: InteractiveFlowRegistry,
        email: String = DEFAULT_EMAIL,
        password: String = DEFAULT_PASSWORD,
    ): String = requireCode(signUp(registry, email, password))

    /** The code [result] captured at the client callback, or a failure saying it carried none. */
    protected fun requireCode(result: AuthorizationResult): String =
        checkNotNull(result.code()) { "expected an authorization code from sign-up" }

    /** The id token of a complete sign-in with [login], as the server signed it. */
    protected fun signIn(
        registry: InteractiveFlowRegistry,
        login: String,
        password: String = DEFAULT_PASSWORD,
    ): String = requireNotNull(
        registry.newFlow()
            .withSignInHandler { Credentials.of(login, password) }
            .run()
            .exchange()
            .idToken(),
    ) { "the openid scope should yield an id_token for '$login'" }

    /** [idToken], or a failure saying the grant it came from should have carried one. */
    protected fun requireIdToken(idToken: String?): String =
        requireNotNull(idToken) { "the openid scope should yield an id_token" }

    /** The subject of [idToken], read off the claims [verifyIdTokenSignature] validated. */
    protected fun subjectOf(sympauthy: SympauthyContainer, idToken: String?): String =
        verifyIdTokenSignature(sympauthy, requireIdToken(idToken)).subject

    /**
     * The signed internal state the `303` answering an authorization request carries, which is what the
     * state-secured flow endpoints are driven with. [authorize] has to be a redirect-carrying response, so
     * the client that issued it must not have followed it.
     */
    protected fun internalState(authorize: FlowCall): String = stateOf(authorize.location)

    /** [internalState] for a response read through [httpGet], which carries its headers rather than one. */
    protected fun internalState(authorize: HttpResponse<String>): String =
        stateOf(authorize.headers().firstValue("Location").orElse(null))

    private fun stateOf(location: String?): String {
        val redirect = location ?: error("authorize 303 had no Location")
        return queryParam(redirect, "state") ?: error("authorize redirect did not carry a state: $redirect")
    }

    // --- HTTP / JWT helpers -------------------------------------------------------------------------

    /**
     * The OpenID Connect discovery document served by [sympauthy], read through the generated client and
     * then held: reading it costs an [ApplicationContext] and a round-trip, and a running container does
     * not change what it publishes. Keyed by the container's address, so a scenario starting two of them
     * reads each.
     */
    protected fun discovery(sympauthy: SympauthyContainer): OpenIdConfigurationResource =
        discoveryDocuments.computeIfAbsent(sympauthy.baseUrl) {
            withApiClient(sympauthy) { ctx ->
                ctx.getBean(OpeniddiscoveryApi::class.java).getConfiguration().block()
            } ?: error("discovery endpoint returned an empty body")
        }

    /**
     * Mints a `client_credentials` access token for [registry]'s (confidential) client carrying [scopes],
     * via the library's [TokenClient]. Used to authenticate a client against the client-API endpoints.
     */
    protected fun clientCredentialsToken(
        sympauthy: SympauthyContainer,
        registry: InteractiveFlowRegistry,
        vararg scopes: String,
    ): String = TokenClient(discovery(sympauthy).tokenEndpoint, HttpClient.newHttpClient(), registry.client())
        .clientCredentials(*scopes)
        .accessToken()

    /**
     * Verifies [idToken] is genuinely signed by the key set [sympauthy] advertises at its `jwks_uri`,
     * returning the validated claims. Throws if the signature does not verify against the JWKS.
     */
    protected fun verifyIdTokenSignature(sympauthy: SympauthyContainer, idToken: String): JWTClaimsSet {
        val jwksUri = discovery(sympauthy).jwksUri
        val signedJwt = SignedJWT.parse(idToken)
        val jwkSource = jwkSources.computeIfAbsent(jwksUri) {
            JWKSourceBuilder.create<SecurityContext>(URI.create(it).toURL()).build()
        }
        val processor = DefaultJWTProcessor<SecurityContext>().apply {
            jwsKeySelector = JWSVerificationKeySelector(signedJwt.header.algorithm, jwkSource)
        }
        return processor.process(signedJwt, null)
    }

    /**
     * The `at_hash` an id token must carry for [accessToken] — the left half of its SHA-256 digest,
     * base64url-encoded (OpenID Connect Core §3.1.3.6). SHA-256 is the hash of `es256`, the shipped
     * default for `advanced.jwt.public-alg`, which no scenario here overrides. A default moving to a
     * 384- or 512-bit algorithm is a change to this digest.
     */
    protected fun expectedAtHash(accessToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(accessToken.toByteArray(StandardCharsets.US_ASCII))
        return Base64URL.encode(digest.copyOf(digest.size / 2)).toString()
    }

    /**
     * A valid authorization request URL for the mock frontend's public client, with a fresh PKCE
     * challenge. It echoes [CLIENT_STATE] back on the redirect it ends at.
     */
    protected fun authorizeUrl(
        sympauthy: SympauthyContainer,
        registry: InteractiveFlowRegistry,
    ): String {
        val params = linkedMapOf(
            "response_type" to "code",
            "client_id" to registry.clientId(),
            "redirect_uri" to registry.redirectUri(),
            "scope" to "openid",
            "state" to CLIENT_STATE,
            "code_challenge" to generatePkce().challenge,
            "code_challenge_method" to "S256",
        )
        val query = params.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        return "${discovery(sympauthy).authorizationEndpoint}?$query"
    }

    protected fun httpGet(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse<String> =
        client().send(
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
        client().send(
            HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(form)))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    // --- Token endpoint -----------------------------------------------------------------------------

    /**
     * Posts an `authorization_code` exchange of [code] for [registry]'s client, answering the raw
     * response so a refusal is read rather than thrown. Each entry of [overrides] replaces the form field
     * it names, and a null value removes that field entirely.
     *
     * The request carries `client_id` and no client authentication, so [registry]'s client has to be a
     * public one; a confidential client is answered `invalid_client` before anything else is read.
     *
     * The `code_verifier` is freshly generated and so cannot hash to the challenge the code carries. That
     * is what the PKCE scenarios want and it is harmless to the others only because of an order the token
     * endpoint owns: it resolves the code's own client, then compares `redirect_uri`, and verifies PKCE
     * last — so a scenario forging any of those is refused before the verifier is read. A scenario whose
     * rule is checked after PKCE cannot use this helper, because the verifier would answer first.
     */
    protected fun exchangeCode(
        sympauthy: SympauthyContainer,
        registry: InteractiveFlowRegistry,
        code: String,
        overrides: Map<String, String?> = emptyMap(),
    ): HttpResponse<String> {
        val form = linkedMapOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to registry.redirectUri(),
            "client_id" to registry.clientId(),
            "code_verifier" to generatePkce().verifier,
        )
        overrides.forEach { (key, value) -> if (value == null) form.remove(key) else form[key] = value }
        return httpPostForm(discovery(sympauthy).tokenEndpoint, form)
    }

    /**
     * Presents [refreshToken] at the token endpoint as [registry]'s confidential client, and answers the
     * parsed success body. That the grant succeeded is asserted here, there being nothing to read off a
     * refusal.
     */
    protected fun refresh(
        sympauthy: SympauthyContainer,
        registry: InteractiveFlowRegistry,
        refreshToken: String,
    ): Map<String, Any> {
        val response = httpPostForm(
            discovery(sympauthy).tokenEndpoint,
            mapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken),
            basicAuth(registry),
        )
        assertEquals(200, response.statusCode(), "the refresh grant should succeed, body=${response.body()}")
        return JSONObjectUtils.parse(response.body())
    }

    /**
     * Introspects [token] as the client [auth] authenticates, and answers the parsed response. That the
     * endpoint answered at all is asserted here; whether the token is active is read off `active`, which
     * RFC 7662 makes the only member an inactive response carries.
     */
    protected fun introspect(
        sympauthy: SympauthyContainer,
        token: String,
        auth: Map<String, String>,
    ): Map<String, Any> {
        val response = httpPostForm(
            checkNotNull(discovery(sympauthy).introspectionEndpoint) {
                "the deployment publishes no introspection endpoint"
            },
            mapOf("token" to token),
            auth,
        )
        assertEquals(200, response.statusCode(), "introspection should answer, body=${response.body()}")
        return JSONObjectUtils.parse(response.body())
    }

    /**
     * Asserts [response] refused with HTTP [status] and that its `error_code` is exactly [error], the
     * RFC 6749 value a client branches on, reporting [message] when it did not. The member is compared
     * rather than searched for in the body: `description` is a bundle message, so a body containing the
     * code is not the same thing as a body naming it, and a rewrite of that sentence must not decide
     * whether this passes.
     */
    protected fun assertOAuthError(
        response: HttpResponse<String>,
        status: Int,
        error: String,
        message: String,
    ) {
        assertEquals(status, response.statusCode(), "$message, body=${response.body()}")
        assertEquals(
            error,
            JSONObjectUtils.parse(response.body())["error_code"],
            "$message — the refusal should name $error, body=${response.body()}",
        )
    }

    /** The `Authorization: Basic` header authenticating the confidential client [clientId] with [secret]. */
    protected fun basicAuth(clientId: String, secret: String): Map<String, String> = mapOf(
        "Authorization" to "Basic " + Base64.getEncoder()
            .encodeToString("$clientId:$secret".toByteArray(StandardCharsets.UTF_8)),
    )

    /**
     * [basicAuth] for [registry]'s own client, which has to be a confidential one — a public client holds
     * no secret to authenticate with.
     */
    protected fun basicAuth(registry: InteractiveFlowRegistry): Map<String, String> = basicAuth(
        registry.clientId(),
        checkNotNull(registry.clientSecret()) { "a public client has no secret to authenticate with" },
    )

    /**
     * An HTTP client that does not follow redirects, so a `303` is an outcome a scenario can assert on
     * rather than a hop it is carried through.
     */
    private fun client(): HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    private fun formEncode(form: Map<String, String>): String =
        form.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }

    protected fun encode(value: String): String =
        java.net.URLEncoder.encode(value, StandardCharsets.UTF_8)

    /** The value of query parameter [name] in [url] (decoded), or null if absent. */
    protected fun queryParam(url: String, name: String): String? = queryParams(url)[name]

    /** The decoded query parameters of [url], in order. */
    private fun queryParams(url: String): Map<String, String> {
        val query = URI.create(url).rawQuery ?: return emptyMap()
        return query.split("&").filter { it.isNotEmpty() }.associate { pair ->
            val separator = pair.indexOf('=')
            val key = if (separator < 0) pair else pair.substring(0, separator)
            val value = if (separator < 0) "" else pair.substring(separator + 1)
            java.net.URLDecoder.decode(key, StandardCharsets.UTF_8) to
                java.net.URLDecoder.decode(value, StandardCharsets.UTF_8)
        }
    }

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

    private val discoveryDocuments = ConcurrentHashMap<String, OpenIdConfigurationResource>()

    private val jwkSources = ConcurrentHashMap<String, JWKSource<SecurityContext>>()

    companion object {

        /**
         * The address a scenario signs its person up with, where the address itself proves nothing. Named
         * apart from the `EMAIL` several scenarios declare for an address they do prove something about,
         * so that deleting one of those does not silently move its sign-up onto this one.
         */
        const val DEFAULT_EMAIL = "ada@example.com"

        /** The password a scenario signs its person up with, valid against the shipped validation rules. */
        const val DEFAULT_PASSWORD = "Str0ngP@ssw0rd!"

        /** The `state` [authorizeUrl] sends, which the server echoes back on the redirect it ends at. */
        const val CLIENT_STATE = "integration-test-state"
    }
}
