package com.sympauthy.monitoring.telemetry

import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.propagation.ReactorPropagation
import io.micronaut.r2dbc.R2dbcConnectionFactoryBean
import io.micronaut.tracing.opentelemetry.OpenTelemetryPropagationContext
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlQuery
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlQueryAnalyzer
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor
import io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE
import io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE
import io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY
import io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT
import io.opentelemetry.semconv.DbAttributes.DB_RESPONSE_STATUS_CODE
import io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME
import io.opentelemetry.semconv.DbAttributes.DbSystemNameValues
import io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE
import io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS
import io.opentelemetry.semconv.ServerAttributes.SERVER_PORT
import io.r2dbc.proxy.ProxyConnectionFactory
import io.r2dbc.proxy.core.MethodExecutionInfo
import io.r2dbc.proxy.core.QueryExecutionInfo
import io.r2dbc.proxy.core.ValueStore
import io.r2dbc.proxy.listener.ProxyExecutionListener
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.ConnectionFactoryOptions.DATABASE
import io.r2dbc.spi.ConnectionFactoryOptions.DRIVER
import io.r2dbc.spi.ConnectionFactoryOptions.HOST
import io.r2dbc.spi.ConnectionFactoryOptions.PORT
import io.r2dbc.spi.R2dbcException
import reactor.util.context.ContextView
import java.util.concurrent.CancellationException
import io.opentelemetry.context.Context as TraceContext

/**
 * The key a deployment turns database spans off by, leaving the rest of the telemetry on: the one
 * OpenTelemetry's own R2DBC instrumentation is turned off by.
 */
const val DATABASE_CALL_TRACING_ENABLED = "otel.instrumentation.r2dbc.enabled"

/**
 * The connection factory the driver creates, behind a proxy that traces every query a request makes, as
 * `docs/design/telemetry.md` says.
 *
 * It replaces the framework's own factory, which is the same call without the proxy, so that with telemetry off
 * nothing stands between a repository and its driver.
 */
@Factory
@Requires(property = TELEMETRY_ENABLED, notEquals = "false")
@Requires(property = DATABASE_CALL_TRACING_ENABLED, notEquals = "false")
class DatabaseCallTracingConnectionFactoryFactory {

    @EachBean(ConnectionFactoryOptions::class)
    @Context
    @Replaces(bean = ConnectionFactory::class, factory = R2dbcConnectionFactoryBean::class)
    fun connectionFactory(options: ConnectionFactoryOptions, openTelemetry: OpenTelemetry): ConnectionFactory {
        val listener = DatabaseCallTracingListener(databaseCallInstrumenter(openTelemetry), QueriedDatabase.of(options))
        return ProxyConnectionFactory.builder(ConnectionFactories.get(options))
            .listener(listener)
            .build()
    }
}

/**
 * The instrumenter the client span of a call to the database is opened and closed by: named and described by the
 * semantic conventions for a database call, from a query's statement once its values are replaced.
 */
internal fun databaseCallInstrumenter(openTelemetry: OpenTelemetry): Instrumenter<DatabaseCall, Void> =
    Instrumenter.builder<DatabaseCall, Void>(openTelemetry, DATABASE_CALL_INSTRUMENTATION_SCOPE) { it.spanName }
        .addAttributesExtractor(DatabaseCallAttributesExtractor)
        .buildInstrumenter(SpanKindExtractor.alwaysClient())

private const val DATABASE_CALL_INSTRUMENTATION_SCOPE = "com.sympauthy.database"

/**
 * Opens a client span when a query is sent and closes it when its results have been received, under the span of
 * the request it was made for. A connection being opened and a transaction beginning or ending are each a
 * [DatabaseRoundTrip] spanned alike, since no query covers them.
 *
 * A call made outside a request opens none: a scheduled job's sweep would otherwise be a trace of its own every
 * few seconds, the reason `/health` is excluded from tracing.
 */
internal class DatabaseCallTracingListener(
    private val instrumenter: Instrumenter<DatabaseCall, Void>,
    private val database: QueriedDatabase
) : ProxyExecutionListener {

    override fun beforeQuery(execution: QueryExecutionInfo) {
        val parent = requestContext(execution.valueStore) ?: return
        val query = DatabaseQuery(
            database = database,
            rawStatements = execution.queries.map { it.query },
            batchSize = maxOf(execution.batchSize, execution.bindingsSize)
        )
        if (!instrumenter.shouldStart(parent, query)) return
        val trace = DatabaseCallTrace(instrumenter.start(parent, query), query)
        execution.valueStore.put(DatabaseCallTrace::class.java, trace)
    }

    override fun afterQuery(execution: QueryExecutionInfo) {
        val trace = execution.valueStore.get(DatabaseCallTrace::class.java, DatabaseCallTrace::class.java) ?: return
        instrumenter.end(trace.context, trace.call, null, failureOf(execution))
    }

    override fun beforeMethod(execution: MethodExecutionInfo) {
        val operation = DatabaseRoundTrip.operationOfOrNull(execution.method.name) ?: return
        val parent = requestContext(execution.valueStore) ?: return
        val roundTrip = DatabaseRoundTrip(database, operation)
        if (!instrumenter.shouldStart(parent, roundTrip)) return
        val trace = DatabaseCallTrace(instrumenter.start(parent, roundTrip), roundTrip)
        execution.valueStore.put(DatabaseCallTrace::class.java, trace)
    }

    /**
     * End the span [beforeMethod] opened, once: the connection's creation is reported as it is handed over, and
     * may be reported again if it is cancelled after.
     */
    override fun afterMethod(execution: MethodExecutionInfo) {
        val trace = execution.valueStore.get(DatabaseCallTrace::class.java, DatabaseCallTrace::class.java) ?: return
        execution.valueStore.remove(DatabaseCallTrace::class.java)
        instrumenter.end(trace.context, trace.call, null, failureOf(execution, trace.call))
    }

    /**
     * The trace the query was made under, or `null` when it was made outside any.
     *
     * The trace the reactive chain carries is the request's, and is read first. The thread's is read where the chain
     * carries none, as a query subscribed to directly does.
     */
    private fun requestContext(valueStore: ValueStore): TraceContext? {
        val reactiveContext = valueStore.get(ContextView::class.java, ContextView::class.java)
        val propagated = reactiveContext
            ?.let { ReactorPropagation.findContextElement(it, OpenTelemetryPropagationContext::class.java) }
            ?.map { it.context() }
            ?.filter { it.hasSpan() }
            ?.orElse(null)
        return propagated ?: TraceContext.current().takeIf { it.hasSpan() }
    }

    /**
     * What ended [execution] early, or `null` when it ended with a result.
     *
     * A query cancelled before it produced any result, as one is when its request times out or its caller goes away,
     * is reported as a failure; one cancelled once it has produced a result was read as far as its caller wanted.
     */
    private fun failureOf(execution: QueryExecutionInfo): Throwable? = execution.throwable
        ?: if (execution.isSuccess) null else CancellationException("The query was cancelled before any result.")

    /**
     * What ended the round trip [call] early, or `null` when it completed.
     *
     * A connection the caller stopped waiting for before it was handed one, as it does when its request times out,
     * is reported as a failure. A transaction's round trip answers nothing either way, so only what it threw says
     * it failed.
     */
    private fun failureOf(execution: MethodExecutionInfo, call: DatabaseCall): Throwable? = execution.thrown
        ?: if (call.spanName == DatabaseRoundTrip.CONNECT && execution.result == null) {
            CancellationException("The connection was cancelled before it was opened.")
        } else {
            null
        }

    /**
     * Whether this context holds a span that is recorded: one that is not, as every request's is while traces are
     * off, opens nothing under it.
     */
    private fun TraceContext.hasSpan(): Boolean = Span.fromContext(this).isRecording

    private class DatabaseCallTrace(val context: TraceContext, val call: DatabaseCall)
}

/**
 * The database a connection factory reaches, as the semantic conventions name it.
 */
internal class QueriedDatabase(
    val systemName: String,
    val namespace: String?,
    val serverAddress: String?,
    val serverPort: Int?
) {

    companion object {

        /**
         * The value of `db.system.name` the semantic conventions give H2, which they still list as incubating.
         */
        private const val H2DATABASE = "h2database"

        private const val OTHER_SQL = "other_sql"

        /**
         * The database [options] reach, every value read whichever type the configuration gave it: a value set
         * under a datasource's `options` arrives as a string.
         *
         * H2 runs inside the server, so it has no server address, and its database is named by the last segment of
         * its path, without the settings a URL appends after a semicolon.
         */
        fun of(options: ConnectionFactoryOptions): QueriedDatabase {
            val database = options.getValue(DATABASE)?.toString()
            return when (options.getValue(DRIVER)?.toString()) {
                "h2" -> QueriedDatabase(
                    systemName = H2DATABASE,
                    namespace = database?.substringBefore(';')?.substringAfterLast('/'),
                    serverAddress = null,
                    serverPort = null
                )

                "postgresql" -> atServer(DbSystemNameValues.POSTGRESQL, database, options)
                else -> atServer(OTHER_SQL, database, options)
            }
        }

        private fun atServer(systemName: String, database: String?, options: ConnectionFactoryOptions) =
            QueriedDatabase(
                systemName = systemName,
                namespace = database,
                serverAddress = options.getValue(HOST)?.toString(),
                serverPort = options.getValue(PORT)?.toString()?.toIntOrNull()
            )
    }
}

/**
 * A round trip to [database] a request waits on, named by [spanName].
 */
internal sealed interface DatabaseCall {
    val database: QueriedDatabase
    val spanName: String
}

/**
 * A round trip to [database] that is not a statement the server sent, named by [operation]: a connection being
 * opened, or a transaction beginning, committing or rolling back.
 */
internal class DatabaseRoundTrip(
    override val database: QueriedDatabase,
    val operation: String
) : DatabaseCall {

    override val spanName: String get() = operation

    companion object {
        const val CONNECT = "connect"
        const val BEGIN = "BEGIN"
        const val COMMIT = "COMMIT"
        const val ROLLBACK = "ROLLBACK"

        /**
         * The operation a call to the method [methodName] of a connection factory or a connection makes, or null
         * where that method is no round trip of its own.
         */
        fun operationOfOrNull(methodName: String): String? = when (methodName) {
            "create" -> CONNECT
            "beginTransaction" -> BEGIN
            "commitTransaction" -> COMMIT
            "rollbackTransaction" -> ROLLBACK
            else -> null
        }
    }
}

/**
 * A query sent to [database], holding its [statements] only once every value in them has been replaced.
 *
 * The server binds its values, so what it sends holds none; a literal written into a statement is replaced too,
 * since nothing tells a value apart from what a person typed.
 */
internal class DatabaseQuery(
    override val database: QueriedDatabase,
    rawStatements: List<String>,
    val batchSize: Int
) : DatabaseCall {

    val statements: List<SqlQuery> = rawStatements.map { analyzer.analyzeWithSummary(it, DIALECT) }

    /**
     * The operation and the table a single statement names, which is what the span is named by.
     */
    val summary: String? = statements.singleOrNull()?.querySummary

    override val spanName: String get() = summary ?: database.systemName

    companion object {
        private val analyzer = SqlQueryAnalyzer.create(true)

        /**
         * PostgreSQL and H2 both quote an identifier in double quotes, as every generated query does. Read as a
         * literal, each would be replaced, and the statement would no longer say what it read.
         */
        private val DIALECT = SqlDialect.DOUBLE_QUOTES_ARE_IDENTIFIERS
    }
}

/**
 * The attributes the semantic conventions name for a database call: the database's on every one, and the statement
 * on a query.
 */
internal object DatabaseCallAttributesExtractor : AttributesExtractor<DatabaseCall, Void> {

    override fun onStart(attributes: AttributesBuilder, parentContext: TraceContext, call: DatabaseCall) {
        val database = call.database
        attributes.put(DB_SYSTEM_NAME, database.systemName)
        database.namespace?.let { attributes.put(DB_NAMESPACE, it) }
        database.serverAddress?.let { attributes.put(SERVER_ADDRESS, it) }
        database.serverPort?.let { attributes.put(SERVER_PORT, it.toLong()) }
        if (call !is DatabaseQuery) return
        attributes.put(DB_QUERY_TEXT, call.statements.mapNotNull { it.queryText }.joinToString("; "))
        call.summary?.let { attributes.put(DB_QUERY_SUMMARY, it) }
        if (call.batchSize > 1) {
            attributes.put(DB_OPERATION_BATCH_SIZE, call.batchSize.toLong())
        }
    }

    override fun onEnd(
        attributes: AttributesBuilder,
        context: TraceContext,
        call: DatabaseCall,
        response: Void?,
        error: Throwable?
    ) {
        if (error == null) return
        val sqlState = (error as? R2dbcException)?.sqlState
        sqlState?.let { attributes.put(DB_RESPONSE_STATUS_CODE, it) }
        attributes.put(ERROR_TYPE, sqlState ?: error.javaClass.name)
    }
}
