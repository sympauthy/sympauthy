package com.sympauthy.data.postgresql.repository

import com.sympauthy.data.postgresql.DefaultDataSourceIsPostgreSQL
import com.sympauthy.data.repository.MailQueueRepository
import io.micronaut.context.annotation.Requires
import io.micronaut.data.model.query.builder.sql.Dialect.POSTGRES
import io.micronaut.data.r2dbc.annotation.R2dbcRepository

@Suppress("unused")
@Requires(condition = DefaultDataSourceIsPostgreSQL::class)
@R2dbcRepository(dialect = POSTGRES)
interface PostgreSQLMailQueueRepository : MailQueueRepository