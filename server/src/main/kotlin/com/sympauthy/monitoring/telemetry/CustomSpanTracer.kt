package com.sympauthy.monitoring.telemetry

import io.micronaut.core.async.propagation.KotlinCoroutinePropagation
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.tracing.opentelemetry.OpenTelemetryPropagationContext
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan
import jakarta.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Opens the spans the server names itself under the span of the request it serves — a wait no other span
 * measures, and a stage of a request its route does not name — and marks that request's span with the interactive
 * flow session it acts on, as `docs/design/telemetry.md` says.
 *
 * It is built on the `OpenTelemetry` bean, OpenTelemetry's no-op while telemetry is off, so the code it wraps has one
 * path whether a collector is named or not.
 */
@Singleton
class CustomSpanTracer(
    openTelemetry: OpenTelemetry
) {

    private val tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE)

    /**
     * Run [block] as the hash of a password for [operation], `create` or `verify`: the wait for a thread reserved
     * for hashing, and the hash.
     */
    suspend fun <T> hashPassword(operation: String, block: suspend () -> T): T =
        inSpan(HASH_PASSWORD, Attributes.of(OPERATION, operation)) { block() }

    /**
     * Run [block] as a request waiting for the signing key [key] names, which the server does not hold yet.
     */
    suspend fun <T> loadSigningKey(key: String, block: suspend () -> T): T =
        inSpan(LOAD_SIGNING_KEY, Attributes.of(KEY, key)) { block() }

    /**
     * Run [block] as the generation of the signing key [key] names, which does not exist yet.
     */
    suspend fun <T> generateSigningKey(key: String, block: suspend () -> T): T =
        inSpan(GENERATE_SIGNING_KEY, Attributes.of(KEY, key)) { block() }

    /**
     * Run [block] as the check of an access token presented under [scheme], `bearer` or `dpop`. [block] says how
     * it ended on the span it is handed.
     */
    suspend fun <T> validateAccessToken(scheme: String, block: suspend (StageSpan) -> T): T =
        inSpan(VALIDATE_ACCESS_TOKEN, Attributes.of(SCHEME, scheme)) { block(StageSpan(it)) }

    /**
     * Run [block] as the check of the signed state a flow request names its interactive flow session by. [block]
     * says how it ended on the span it is handed.
     */
    suspend fun <T> validateState(block: suspend (StageSpan) -> T): T =
        inSpan(VALIDATE_STATE) { block(StageSpan(it)) }

    /**
     * Run [block] as the walk of an interactive flow session's purposes a flow request ends with.
     */
    suspend fun <T> advanceFlow(block: suspend () -> T): T = inSpan(ADVANCE_FLOW) { block() }

    /**
     * Run [block] as the walk reaching [purpose], spelled as the flow metrics spell it.
     */
    suspend fun <T> resolvePurpose(purpose: String, block: suspend () -> T): T =
        inSpan("resolve $purpose", Attributes.of(PURPOSE, purpose)) { block() }

    /**
     * Run [block] as the completion that ends the walk of an interactive flow session's purposes.
     */
    suspend fun <T> completeFlow(block: suspend () -> T): T = inSpan(COMPLETE_FLOW) { block() }

    /**
     * Run [block] as deciding which account the identity a provider [providerId] names asserted is. [block] says on
     * the span it is handed whether it signed the person in or up.
     */
    suspend fun <T> establishAccount(providerId: String, block: suspend (StageSpan) -> T): T =
        inSpan(ESTABLISH_ACCOUNT, Attributes.of(PROVIDER_ID, providerId)) { block(StageSpan(it)) }

    /**
     * Run [block] as the issue of one token of [token]'s type: [ACCESS_TOKEN], [REFRESH_TOKEN] or [ID_TOKEN].
     */
    suspend fun <T> issueToken(token: String, block: suspend () -> T): T = inSpan("issue $token") { block() }

    /**
     * Run [block] under a span named [name] carrying [attributes], a child of the span the caller runs under, and
     * end it once [block] returns or throws. [block] is handed the span, to add what it learns on the way.
     *
     * No span opens where the caller runs under none, as a scheduled job and the work at startup do, or under one
     * that is not recorded, as every request is while traces are off: [block] is then handed an invalid span, which
     * drops what is written to it.
     *
     * What [block] queries, calls and opens is under the span, and so is what it logs.
     */
    internal suspend fun <T> inSpan(
        name: String,
        attributes: Attributes = Attributes.empty(),
        block: suspend (Span) -> T
    ): T {
        val propagated = KotlinCoroutinePropagation.findPropagatedContext() ?: PropagatedContext.getOrEmpty()
        val parent = propagated.traceContext()
            ?.takeIf { Span.fromContext(it).isRecording }
            ?: return block(Span.getInvalid())
        val span = tracer.spanBuilder(name).setParent(parent).setAllAttributes(attributes).startSpan()
        val child = propagated.plus(OpenTelemetryPropagationContext(parent.with(span)))
        return try {
            withContext(KotlinCoroutinePropagation.addPropagatedContext(EmptyCoroutineContext, child)) {
                block(span)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
            span.setStatus(StatusCode.ERROR)
            throw failure
        } finally {
            span.end()
        }
    }

    /**
     * The span of the request the caller serves, as a reference a session can keep, or null where that span is
     * not recorded: a link to it would lead nowhere.
     */
    suspend fun requestSpanReference(): SpanReference? {
        val span = requestSpan() ?: return null
        return span.spanContext.takeIf { span.isRecording && it.isSampled }?.let(SpanReference::of)
    }

    /**
     * Mark the span of the request the caller serves with [sessionId], the interactive flow session it acts on, and
     * link it to [startedBy], the span of the request that started that session, where the session kept one.
     *
     * The request's own span is marked rather than the stage the caller runs under, so that a search on the session
     * lands on the request.
     */
    suspend fun markFlowSession(sessionId: UUID, startedBy: SpanReference?) {
        val span = requestSpan()?.takeIf(Span::isRecording) ?: return
        span.setAttribute(SESSION_ID, sessionId.toString())
        startedBy
            ?.toSpanContext()
            ?.takeIf { it != span.spanContext }
            ?.let(span::addLink)
    }

    /**
     * The span of the request the caller serves: the first span opened in this process for it, which the framework
     * opens before any route.
     */
    private suspend fun requestSpan(): Span? {
        val propagated = KotlinCoroutinePropagation.findPropagatedContext() ?: PropagatedContext.getOrEmpty()
        return propagated.traceContext()?.let(LocalRootSpan::fromContextOrNull)
    }

    private fun PropagatedContext.traceContext(): Context? = find(OpenTelemetryPropagationContext::class.java)
        .map(OpenTelemetryPropagationContext::context)
        .orElse(null)
        ?.takeIf { Span.fromContext(it).spanContext.isValid }

    companion object {
        private const val INSTRUMENTATION_SCOPE = "com.sympauthy.request"

        const val HASH_PASSWORD = "hash password"
        const val LOAD_SIGNING_KEY = "load signing_key"
        const val GENERATE_SIGNING_KEY = "generate signing_key"
        const val VALIDATE_ACCESS_TOKEN = "validate access_token"
        const val VALIDATE_STATE = "validate state"
        const val ADVANCE_FLOW = "advance flow"
        const val COMPLETE_FLOW = "complete flow"
        const val ESTABLISH_ACCOUNT = "establish account"

        const val ACCESS_TOKEN = "access_token"
        const val REFRESH_TOKEN = "refresh_token"
        const val ID_TOKEN = "id_token"

        /**
         * The interactive flow session a request acts on, under the semantic conventions' name for a session.
         */
        val SESSION_ID: AttributeKey<String> = AttributeKey.stringKey("session.id")

        private val OPERATION = AttributeKey.stringKey("operation")
        private val KEY = AttributeKey.stringKey("key")
        private val SCHEME = AttributeKey.stringKey("scheme")
        private val PURPOSE = AttributeKey.stringKey("purpose")
        private val PROVIDER_ID = AttributeKey.stringKey("provider_id")
        private val KIND = AttributeKey.stringKey("kind")
    }

    /**
     * A span a stage of a request is recorded under, to which the stage adds what it learns on the way.
     */
    class StageSpan internal constructor(
        private val span: Span
    ) {

        /**
         * Say how the stage ended, under `outcome` and `error.type`.
         */
        fun setOutcome(outcome: Outcome) {
            span.setAttribute(OUTCOME, outcome.value)
            outcome.errorType?.let { span.setAttribute(ERROR_TYPE, it) }
        }

        /**
         * Say which client the stage was for, once the client has been found.
         */
        fun setClientId(clientId: String) {
            span.setAttribute(CLIENT_ID, clientId)
        }

        /**
         * Say whether the stage signed a person in or up, as `kind` spells it on the authentication metric.
         */
        fun setKind(kind: String) {
            span.setAttribute(KIND, kind)
        }
    }
}

/**
 * A span this server recorded, kept by its trace and span id so that a later request can link to it.
 */
data class SpanReference(
    val traceId: String,
    val spanId: String
) {

    /**
     * The span this references, as a link names it: sampled, since only a recorded span is ever kept, and remote,
     * since the request that recorded it has ended.
     */
    fun toSpanContext(): SpanContext? = SpanContext.createFromRemoteParent(
        traceId,
        spanId,
        TraceFlags.getSampled(),
        TraceState.getDefault()
    ).takeIf(SpanContext::isValid)

    companion object {

        fun of(spanContext: SpanContext): SpanReference = SpanReference(spanContext.traceId, spanContext.spanId)

        /**
         * The reference a row keeps in its two columns, or null where it kept none.
         */
        fun ofOrNull(traceId: String?, spanId: String?): SpanReference? =
            if (traceId != null && spanId != null) SpanReference(traceId, spanId) else null
    }
}
