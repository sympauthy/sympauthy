package com.sympauthy.it

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jose.util.JSONObjectUtils
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.sympauthy.api.client.api.Oauth2Api
import com.sympauthy.api.client.api.OpeniddiscoveryApi
import com.sympauthy.api.client.model.GetTokensRequestGrantType
import com.sympauthy.api.client.model.IntrospectionResource
import com.sympauthy.api.client.model.OpenIdConfigurationResource
import com.sympauthy.api.client.model.TokenResource
import com.sympauthy.it.client.BearerTokenHolder
import com.sympauthy.it.client.FlowCall
import com.sympauthy.it.client.FlowHttpClient
import com.sympauthy.it.client.FlowStateHolder
import com.sympauthy.testcontainers.Client
import com.sympauthy.testcontainers.SympauthyContainer
import com.sympauthy.testcontainers.flow.AuthorizationResult
import com.sympauthy.testcontainers.flow.Credentials
import com.sympauthy.testcontainers.flow.InteractiveFlowRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.exceptions.HttpClientResponseException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

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
     * token to every client request (see `BearerTokenClientFilter`), and when [state] is non-null it is
     * attached to every flow-surface request where the server reads it (see `FlowStateClientFilter`). The
     * context is closed on exit.
     *
     * Obtain the typed API beans inside the block, e.g. `ctx.getBean(AdminApi::class.java)`; their methods
     * return `reactor.core.publisher.Mono`, so call `.block()` to get the deserialized resource.
     *
     * The client's JSON codec also reads [JWK_SET_MEDIA_TYPE], the type the key set is answered with
     * first: the codec reads only `application/json` unless told otherwise.
     */
    protected fun <T> withApiClient(
        sympauthy: SympauthyContainer,
        token: String? = null,
        state: String? = null,
        block: (ApplicationContext) -> T,
    ): T = ApplicationContext.run(
        mapOf(
            "micronaut.http.services.sympauthy.url" to sympauthy.baseUrl,
            "micronaut.codec.json.additional-types" to listOf(JWK_SET_MEDIA_TYPE),
        ),
    ).use { ctx ->
        ctx.getBean(BearerTokenHolder::class.java).token = token
        ctx.getBean(FlowStateHolder::class.java).state = state
        block(ctx)
    }

    /**
     * Runs [block] with a [FlowHttpClient] bound to [sympauthy] that does **not** follow redirects, so a
     * scenario can read the `/authorize` `303` `Location`, which carries the signed internal state the
     * generated client then drives the flow with. The hosting context is closed on exit.
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

    /** Signs a person up as [signUp] does, and exchanges the code it yields for tokens with [exchange]. */
    protected fun signUpAndExchange(
        sympauthy: SympauthyContainer,
        registry: InteractiveFlowRegistry,
        email: String = DEFAULT_EMAIL,
        password: String = DEFAULT_PASSWORD,
    ): TokenResource = exchange(sympauthy, registry, signUp(registry, email, password))

    /** The id token of a complete sign-in with [login], as the server signed it. */
    protected fun signIn(
        sympauthy: SympauthyContainer,
        registry: InteractiveFlowRegistry,
        login: String,
        password: String = DEFAULT_PASSWORD,
    ): String {
        val signedIn = registry.newFlow()
            .withSignInHandler { Credentials.of(login, password) }
            .run()
        return requireNotNull(exchange(sympauthy, registry, signedIn).idToken) {
            "the openid scope should yield an id_token for '$login'"
        }
    }

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
     * Mints a `client_credentials` access token carrying [scopes] through the generated client, for the
     * confidential client [clientId] sending [clientSecret] in the body. A refusal throws.
     */
    protected fun clientCredentialsToken(
        sympauthy: SympauthyContainer,
        clientId: String,
        clientSecret: String,
        vararg scopes: String,
    ): String = withApiClient(sympauthy) { ctx ->
        ctx.getBean(Oauth2Api::class.java).getTokens(
            grantType = GetTokensRequestGrantType.CLIENT_CREDENTIALS,
            scope = scopes.joinToString(" ").ifEmpty { null },
            clientId = clientId,
            clientSecret = clientSecret,
        ).block()
    }?.accessToken ?: error("the client_credentials grant answered an empty body")

    /**
     * [clientCredentialsToken] for [registry]'s own client, which has to be a confidential one. Used to
     * authenticate a client against the client-API endpoints.
     */
    protected fun clientCredentialsToken(
        sympauthy: SympauthyContainer,
        registry: InteractiveFlowRegistry,
        vararg scopes: String,
    ): String = clientCredentialsToken(sympauthy, registry.clientId(), requireSecret(registry), *scopes)

    /**
     * Verifies [idToken] is genuinely signed by the key set [sympauthy] publishes, returning the validated
     * claims. Throws if the signature does not verify against that key set.
     */
    protected fun verifyIdTokenSignature(sympauthy: SympauthyContainer, idToken: String): JWTClaimsSet {
        val signedJwt = SignedJWT.parse(idToken)
        val processor = DefaultJWTProcessor<SecurityContext>().apply {
            jwsKeySelector = JWSVerificationKeySelector(signedJwt.header.algorithm, publicKeySet(sympauthy))
        }
        return processor.process(signedJwt, null)
    }

    /**
     * The key set [sympauthy] publishes, read through the generated client and then held, for the reason
     * [discovery] holds the discovery document.
     */
    private fun publicKeySet(sympauthy: SympauthyContainer): JWKSource<SecurityContext> =
        publicKeySets.computeIfAbsent(sympauthy.baseUrl) {
            val keySet = withApiClient(sympauthy) { ctx ->
                ctx.getBean(OpeniddiscoveryApi::class.java).getPublicKey().block()
            } ?: error("the key set endpoint returned an empty body")
            ImmutableJWKSet(JWKSet.parse(keySet))
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
     * A valid authorization request URL for the mock frontend's public client, carrying the challenge of
     * a fresh PKCE pair nobody holds. It echoes [CLIENT_STATE] back on the redirect it ends at.
     */
    protected fun authorizeUrl(sympauthy: SympauthyContainer, registry: InteractiveFlowRegistry): String {
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
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    protected fun httpPost(
        url: String,
        contentType: String,
        body: String,
    ): HttpResponse<String> =
        client().send(
            HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", contentType)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
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
     * Exchanges the code of [result] at the token endpoint through the generated client, as [registry]'s
     * client with its credentials in the body — `client_id` alone for a public one — and presenting the
     * verifier of the authorization [result] answers. Answers the tokens issued, and throws on a refusal.
     */
    protected fun exchange(
        sympauthy: SympauthyContainer,
        registry: InteractiveFlowRegistry,
        result: AuthorizationResult,
    ): TokenResource = withApiClient(sympauthy) { ctx ->
        ctx.getBean(Oauth2Api::class.java).exchange(registry, result).block()
    } ?: error("the code exchange answered an empty body")

    /**
     * Exchanges the code of [result] as [exchange] does, and asserts the token endpoint refuses it with
     * HTTP [status] and [error], reporting [message] when it does not.
     */
    protected fun assertExchangeRefused(
        sympauthy: SympauthyContainer,
        registry: InteractiveFlowRegistry,
        result: AuthorizationResult,
        status: Int,
        error: String,
        message: String,
    ) {
        withApiClient(sympauthy) { ctx ->
            val refusal = assertThrows<HttpClientResponseException>(message) {
                ctx.getBean(Oauth2Api::class.java).exchange(registry, result).block()
            }
            assertOAuthError(refusal, status, error, message)
        }
    }

    /**
     * The `authorization_code` grant of [result]'s code and verifier, as [registry]'s client with its
     * credentials in the body.
     */
    private fun Oauth2Api.exchange(registry: InteractiveFlowRegistry, result: AuthorizationResult) = getTokens(
        grantType = GetTokensRequestGrantType.AUTHORIZATION_CODE,
        code = requireCode(result),
        clientId = registry.clientId(),
        clientSecret = registry.clientSecret(),
        codeVerifier = result.codeVerifier(),
    )

    /**
     * Posts an `authorization_code` exchange of [result]'s code and verifier for [registry]'s client, as a
     * form built by hand, answering the raw response so a refusal is read rather than thrown. Each entry
     * of [overrides] replaces the form field it names, and a null value removes that field entirely;
     * [headers] are sent beside the form.
     *
     * The request carries `client_id` and no client authentication, so [registry]'s client has to be a
     * public one; a confidential client is answered `invalid_client` before anything else is read.
     */
    protected fun exchangeCode(
        sympauthy: SympauthyContainer,
        registry: InteractiveFlowRegistry,
        result: AuthorizationResult,
        overrides: Map<String, String?> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse<String> {
        val form = linkedMapOf(
            "grant_type" to "authorization_code",
            "code" to requireCode(result),
            "client_id" to registry.clientId(),
            "code_verifier" to result.codeVerifier(),
        )
        overrides.forEach { (key, value) -> if (value == null) form.remove(key) else form[key] = value }
        return httpPostForm(discovery(sympauthy).tokenEndpoint, form, headers)
    }

    /** The code [result] captured at the client callback, or a failure saying it carried none. */
    private fun requireCode(result: AuthorizationResult): String =
        checkNotNull(result.code()) { "the flow should have yielded an authorization code" }

    /**
     * Presents [refreshToken] at the token endpoint through the generated client, as [registry]'s client
     * with its credentials in the body — `client_id` alone for a public one — and answers the tokens
     * issued. A refusal throws, there being nothing to read off one.
     */
    protected fun refresh(
        sympauthy: SympauthyContainer,
        registry: InteractiveFlowRegistry,
        refreshToken: String,
    ): TokenResource = withApiClient(sympauthy) { ctx ->
        ctx.getBean(Oauth2Api::class.java).refresh(registry, refreshToken).block()
    } ?: error("the refresh grant answered an empty body")

    /**
     * Presents [refreshToken] as [refresh] does, and asserts the token endpoint refuses it with HTTP
     * [status] and [error], reporting [message] when it does not.
     */
    protected fun assertRefreshRefused(
        sympauthy: SympauthyContainer,
        registry: InteractiveFlowRegistry,
        refreshToken: String,
        status: Int,
        error: String,
        message: String,
    ) {
        withApiClient(sympauthy) { ctx ->
            val refusal = assertThrows<HttpClientResponseException>(message) {
                ctx.getBean(Oauth2Api::class.java).refresh(registry, refreshToken).block()
            }
            assertOAuthError(refusal, status, error, message)
        }
    }

    /** The refresh grant for [refreshToken], as [registry]'s client with its credentials in the body. */
    private fun Oauth2Api.refresh(registry: InteractiveFlowRegistry, refreshToken: String) = getTokens(
        grantType = GetTokensRequestGrantType.REFRESH_TOKEN,
        refreshToken = refreshToken,
        clientId = registry.clientId(),
        clientSecret = registry.clientSecret(),
    )

    /**
     * Introspects [token] through the generated client, as the client [clientId] sending [clientSecret] in
     * the body. Whether the token is active is read off `active`, which RFC 7662 makes the only member an
     * inactive response carries.
     */
    protected fun introspect(
        sympauthy: SympauthyContainer,
        token: String,
        clientId: String,
        clientSecret: String,
    ): IntrospectionResource = withApiClient(sympauthy) { ctx ->
        ctx.getBean(Oauth2Api::class.java)
            .introspectToken(token = token, clientId = clientId, clientSecret = clientSecret)
            .block()
    } ?: error("introspection answered an empty body")

    /** [introspect] as [registry]'s own client, which has to be a confidential one. */
    protected fun introspect(
        sympauthy: SympauthyContainer,
        token: String,
        registry: InteractiveFlowRegistry,
    ): IntrospectionResource = introspect(sympauthy, token, registry.clientId(), requireSecret(registry))

    /**
     * Revokes [token] through the generated client, as the client [clientId] sending [clientSecret] in the
     * body, or `client_id` alone when it is null. A refusal throws; RFC 7009 answers every other outcome
     * alike, so there is nothing to read off the response.
     */
    protected fun revoke(
        sympauthy: SympauthyContainer,
        token: String,
        clientId: String,
        clientSecret: String?,
    ) {
        withApiClient(sympauthy) { ctx ->
            ctx.getBean(Oauth2Api::class.java)
                .revokeToken(token = token, clientId = clientId, clientSecret = clientSecret)
                .block()
        }
    }

    /** [revoke] as [registry]'s own client. */
    protected fun revoke(sympauthy: SympauthyContainer, token: String, registry: InteractiveFlowRegistry) =
        revoke(sympauthy, token, registry.clientId(), registry.clientSecret())

    /**
     * Asserts [response] refused with HTTP [status] and that its `error` is exactly [error], the
     * RFC 6749 value a client branches on, reporting [message] when it did not. The member is compared
     * rather than searched for in the body: `error_description` is a bundle message, so a body
     * containing the code is not the same thing as a body naming it, and a rewrite of that sentence
     * must not decide whether this passes.
     */
    protected fun assertOAuthError(
        response: HttpResponse<String>,
        status: Int,
        error: String,
        message: String,
    ) = assertRefusal(response.statusCode(), response.body(), "error", status, error, message)

    /**
     * [assertOAuthError] for a refusal the generated client threw. Call it inside the client block that
     * made the request: the response buffer is released when the context closes.
     */
    protected fun assertOAuthError(
        refusal: HttpClientResponseException,
        status: Int,
        error: String,
        message: String,
    ) = assertRefusal(refusal.status.code, bodyOf(refusal), "error", status, error, message)

    /**
     * Asserts [response] refused with HTTP [status] in the ordinary error body, and that its
     * `error_code` is exactly [errorCode], reporting [message] when it did not. A refusal on the token,
     * the introspection or the revocation endpoint is answered in the body RFC 6749 defines whatever
     * refused it, and is [assertOAuthError]'s.
     */
    protected fun assertError(
        response: HttpResponse<String>,
        status: Int,
        errorCode: String,
        message: String,
    ) = assertRefusal(response.statusCode(), response.body(), "error_code", status, errorCode, message)

    /**
     * [assertError] for a refusal the generated client threw. Call it inside the client block that made
     * the request: the response buffer is released when the context closes.
     */
    protected fun assertError(
        refusal: HttpClientResponseException,
        status: Int,
        errorCode: String,
        message: String,
    ) = assertRefusal(refusal.status.code, bodyOf(refusal), "error_code", status, errorCode, message)

    /**
     * Asserts a refusal answered [actualStatus] equal to [status] and carries [code] in the member [codeMember]
     * of [body], reporting [message] when it does not.
     */
    private fun assertRefusal(
        actualStatus: Int,
        body: String,
        codeMember: String,
        status: Int,
        code: String,
        message: String,
    ) {
        assertEquals(status, actualStatus, "$message, body=$body")
        assertEquals(
            code,
            JSONObjectUtils.parse(body)[codeMember],
            "$message — the refusal should name $code, body=$body",
        )
    }

    private fun bodyOf(refusal: HttpClientResponseException): String =
        refusal.response.getBody(String::class.java).orElse("")

    /**
     * The `Authorization: Basic` header authenticating the confidential client [clientId] with [secret],
     * each form-encoded as RFC 6749 §2.3.1 requires.
     */
    protected fun basicAuth(clientId: String, secret: String): Map<String, String> {
        val credentials = URLEncoder.encode(clientId, StandardCharsets.UTF_8) + ":" +
            URLEncoder.encode(secret, StandardCharsets.UTF_8)
        return mapOf(
            "Authorization" to "Basic " + Base64.getEncoder()
                .encodeToString(credentials.toByteArray(StandardCharsets.UTF_8)),
        )
    }

    /**
     * [basicAuth] for [registry]'s own client, which has to be a confidential one — a public client holds
     * no secret to authenticate with.
     */
    protected fun basicAuth(registry: InteractiveFlowRegistry): Map<String, String> =
        basicAuth(registry.clientId(), requireSecret(registry))

    /** The secret of [registry]'s client, or a failure saying a public client holds none. */
    private fun requireSecret(registry: InteractiveFlowRegistry): String =
        checkNotNull(registry.clientSecret()) { "a public client has no secret to authenticate with" }

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

    private val publicKeySets = ConcurrentHashMap<String, JWKSource<SecurityContext>>()

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

        /** The media type of a JSON Web Key Set, RFC 7517 §8.5. */
        private const val JWK_SET_MEDIA_TYPE = "application/jwk-set+json"
    }
}
