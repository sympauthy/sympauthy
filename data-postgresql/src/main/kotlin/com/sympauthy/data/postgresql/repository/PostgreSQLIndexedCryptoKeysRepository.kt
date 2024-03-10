package com.sympauthy.data.postgresql.repository

import com.sympauthy.data.postgresql.DefaultDatasourceIsPostgreSQL
import com.sympauthy.data.repository.IndexedCryptoKeysRepository
import io.micronaut.context.annotation.Requires
import io.micronaut.data.model.query.builder.sql.Dialect.POSTGRES
import io.micronaut.data.r2dbc.annotation.R2dbcRepository

@Suppress("unused")
@Requires(condition = DefaultDatasourceIsPostgreSQL::class)
@R2dbcRepository(dialect = POSTGRES)
interface PostgreSQLIndexedCryptoKeysRepository : IndexedCryptoKeysRepository
