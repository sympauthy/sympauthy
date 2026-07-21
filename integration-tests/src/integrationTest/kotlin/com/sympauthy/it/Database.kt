package com.sympauthy.it

import com.sympauthy.testcontainers.SympauthyContainer
import org.testcontainers.containers.Network
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * A database backing a [SympauthyContainer] for a single test run. Each scenario runs against every
 * [Database] via `@EnumSource`, so the same behaviour is exercised on both supported datasources.
 */
enum class Database(private val fixtureFactory: () -> DatabaseFixture) {

    /** SympAuthy's default in-memory H2 datasource — nothing extra to start. */
    H2({ DatabaseFixture.H2 }),

    /** A companion PostgreSQL container SympAuthy reaches over a shared Docker network. */
    POSTGRES({ DatabaseFixture.Postgres() });

    fun createFixture(): DatabaseFixture = fixtureFactory()
}

/**
 * Wires a database into a [SympauthyContainer] before it starts, and owns the lifecycle of any
 * companion container. Close it (after the SympAuthy container) to release those resources.
 */
sealed interface DatabaseFixture : AutoCloseable {

    /** Applies datasource/network settings to [container] before startup, returning it for chaining. */
    fun applyTo(container: SympauthyContainer): SympauthyContainer

    /** Default in-memory H2 (the container's own default): nothing to wire, nothing to close. */
    data object H2 : DatabaseFixture {
        override fun applyTo(container: SympauthyContainer): SympauthyContainer = container
        override fun close() = Unit
    }

    /**
     * A PostgreSQL container on a dedicated network; SympAuthy joins the same network and connects via
     * the [ALIAS] network alias (host `localhost` port mappings are not reachable between containers).
     */
    class Postgres : DatabaseFixture {

        private val network: Network = Network.newNetwork()

        private val postgres: PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse(IMAGE))
            .withNetwork(network)
            .withNetworkAliases(ALIAS)
            .withDatabaseName(DATABASE)
            .withUsername(USERNAME)
            .withPassword(PASSWORD)
            .also { it.start() }

        override fun applyTo(container: SympauthyContainer): SympauthyContainer = container
            .withNetwork(network)
            .withDatasource("r2dbc:postgresql://$ALIAS:$PORT/$DATABASE", USERNAME, PASSWORD)

        override fun close() {
            postgres.stop()
            network.close()
        }

        private companion object {
            const val IMAGE = "postgres:17-alpine"
            const val ALIAS = "postgres"
            const val PORT = 5432
            const val DATABASE = "sympauthy"
            const val USERNAME = "sympauthy"
            const val PASSWORD = "sympauthy"
        }
    }
}
