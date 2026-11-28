package com.sympauthy.data.postgresql

import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider.OPTIONS
import io.r2dbc.spi.ConnectionFactoryOptions
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.postgresql.ds.PGSimpleDataSource

class PostgreSQLDefaultDataSourceFactoryTest {

    private fun provideDataSource(options: ConnectionFactoryOptions): PGSimpleDataSource {
        return PostgreSQLDefaultDataSourceFactory(options).provideDataSource() as PGSimpleDataSource
    }

    @Test
    fun `provideDataSource - Carry the schema, the SSL mode and the connect timeout of the R2DBC url`() {
        val options = ConnectionFactoryOptions.parse(
            "r2dbc:postgresql://user:secret@db.example:6543/sympauthy" +
                "?schema=tenant&sslMode=verify-full&sslRootCert=/etc/ca.pem&connectTimeout=PT2.5S"
        )

        val dataSource = provideDataSource(options)

        assertArrayEquals(arrayOf("db.example"), dataSource.serverNames)
        assertArrayEquals(intArrayOf(6543), dataSource.portNumbers)
        assertEquals("sympauthy", dataSource.databaseName)
        assertEquals("user", dataSource.user)
        assertEquals("secret", dataSource.password)
        assertEquals("tenant", dataSource.currentSchema)
        assertEquals("verify-full", dataSource.sslMode)
        assertEquals("/etc/ca.pem", dataSource.sslRootCert)
        assertEquals(3, dataSource.connectTimeout)
    }

    @Test
    fun `provideDataSource - Verify the certificate when the R2DBC url only enables SSL`() {
        val options = ConnectionFactoryOptions.parse("r2dbcs:postgresql://db.example/sympauthy")

        val dataSource = provideDataSource(options)

        assertEquals("verify-full", dataSource.sslMode)
        assertArrayEquals(intArrayOf(5432), dataSource.portNumbers)
    }

    @Test
    fun `provideDataSource - Pass every startup parameter as a command-line option`() {
        val options = ConnectionFactoryOptions.parse("r2dbc:postgresql://db.example/sympauthy").mutate()
            .option(OPTIONS, mapOf("search_path" to "tenant", "application_name" to "sympauthy migration"))
            .build()

        val dataSource = provideDataSource(options)

        assertEquals("-c search_path=tenant -c application_name=sympauthy\\ migration", dataSource.options)
    }

    @Test
    fun `provideDataSource - Refuse a database reached through several hosts`() {
        val options = ConnectionFactoryOptions.parse("r2dbc:postgresql:failover://db1.example,db2.example/sympauthy")

        assertThrows<IllegalStateException> { provideDataSource(options) }
    }
}
