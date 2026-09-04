package com.sympauthy.data

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextBuilder
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * A database a repository test runs against. Every scenario runs against every [Database] via
 * `@EnumSource`, so a query and a mapping are proved in each dialect's spelling rather than in H2's
 * alone.
 *
 * The dialect is chosen by one property. [com.sympauthy.data.h2.DefaultDataSourceIsH2] and
 * [com.sympauthy.data.postgresql.DefaultDataSourceIsPostgreSQL] select the repository twins by
 * inspecting the R2DBC connection factory, and each dialect's data source factory derives the JDBC
 * connection Flyway migrates through from the same place. Pointing `r2dbc.datasources.default.url` at
 * a database therefore selects its twins and builds its schema.
 *
 * [context] is built on first use and shared by every test in the run, which is what the rule against
 * `deleteAll()` protects. Micronaut's test annotation is not used: it binds one context per class,
 * and the dialect is fixed when that context starts.
 */
enum class Database(
    private val configure: ApplicationContextBuilder.() -> ApplicationContextBuilder
) {

    /** The embedded in-memory H2, configured in `application-h2.yml`. */
    H2({ environments("default", "test", "h2") }),

    /** A PostgreSQL container, started on first use and stopped when the test JVM exits. */
    POSTGRESQL({ environments("default", "test").properties(postgreSQLDatasource()) });

    val context: ApplicationContext by lazy {
        ApplicationContext.builder().configure().build().start()
            .also { Runtime.getRuntime().addShutdownHook(Thread(it::close)) }
    }
}

/** Resolves the repository — or any other bean — of type [T] against this database. */
inline fun <reified T : Any> Database.bean(): T = context.getBean(T::class.java)

/**
 * Named for the image the integration tests run, so both suites prove the same PostgreSQL.
 * Testcontainers stops it once the test JVM exits.
 */
private val postgreSQLContainer by lazy {
    PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
        .withDatabaseName("sympauthy")
        .withUsername("sympauthy")
        .withPassword("sympauthy")
        .also { it.start() }
}

private fun postgreSQLDatasource(): Map<String, Any> = postgreSQLContainer.let {
    mapOf(
        "r2dbc.datasources.default.url" to
            "r2dbc:postgresql://${it.host}:${it.firstMappedPort}/${it.databaseName}",
        "r2dbc.datasources.default.username" to it.username,
        "r2dbc.datasources.default.password" to it.password,
    )
}
