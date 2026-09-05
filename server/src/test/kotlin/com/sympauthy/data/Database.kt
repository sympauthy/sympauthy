package com.sympauthy.data

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextBuilder
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * A database a repository test runs against.
 *
 * The dialect is chosen by one property. [com.sympauthy.data.h2.DefaultDataSourceIsH2] and
 * [com.sympauthy.data.postgresql.DefaultDataSourceIsPostgreSQL] select the repository twins by
 * inspecting the R2DBC connection factory, and each dialect's data source factory derives the JDBC
 * connection Flyway migrates through from the same place. Pointing `r2dbc.datasources.default.url` at
 * a database therefore selects its twins and builds its schema.
 */
enum class Database(
    private val configure: ApplicationContextBuilder.() -> ApplicationContextBuilder
) {

    /** The embedded in-memory H2, configured in `application-h2.yml`. */
    H2({ environments("default", "test", "h2") }),

    /** A PostgreSQL container, started on first use and stopped when the test JVM exits. */
    POSTGRES({ environments("default", "test").properties(postgreSQLDatasource()) });

    /**
     * Held as a [Result] rather than behind `by lazy`, which reruns an initializer that threw: without
     * Docker every parameterized test would start the container again and pay its discovery timeout,
     * burying the one failure that mattered under a hundred identical ones.
     */
    private var started: Result<ApplicationContext>? = null

    val context: ApplicationContext
        @Synchronized get() = (started ?: runCatching { start() }.also { started = it }).getOrThrow()

    private fun start(): ApplicationContext = ApplicationContext.builder().configure().build().start()
        .also { Runtime.getRuntime().addShutdownHook(Thread(it::close)) }
}

/** Resolves the repository — or any other bean — of type [T] against this database. */
inline fun <reified T : Any> Database.bean(): T = context.getBean(T::class.java)

/**
 * Testcontainers stops it once the test JVM exits. The tag is the one
 * `integration-tests/…/it/Database.kt` names; nothing checks that the two agree, so move both together.
 */
private val postgreSQLContainer by lazy {
    PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
        .withDatabaseName("sympauthy")
        .withUsername("sympauthy")
        .withPassword("sympauthy")
        .apply { start() }
}

private fun postgreSQLDatasource(): Map<String, Any> = postgreSQLContainer.let {
    mapOf(
        "r2dbc.datasources.default.url" to
            "r2dbc:postgresql://${it.host}:${it.firstMappedPort}/${it.databaseName}",
        "r2dbc.datasources.default.username" to it.username,
        "r2dbc.datasources.default.password" to it.password,
    )
}
