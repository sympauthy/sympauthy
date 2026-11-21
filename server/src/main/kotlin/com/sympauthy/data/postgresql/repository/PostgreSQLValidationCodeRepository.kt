package com.sympauthy.data.postgresql.repository

import com.sympauthy.data.postgresql.DefaultDataSourceIsPostgreSQL
import com.sympauthy.data.repository.ValidationCodeRepository
import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.model.DataType
import io.micronaut.data.model.query.builder.sql.Dialect.POSTGRES
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import java.time.LocalDateTime
import java.util.*

@Suppress("unused")
@Requires(condition = DefaultDataSourceIsPostgreSQL::class)
@R2dbcRepository(dialect = POSTGRES)
interface PostgreSQLValidationCodeRepository : ValidationCodeRepository {

    @Query(
        """
        INSERT INTO validation_codes
            (code, user_id, media, reasons, session_id, creation_date, resend_date, expiration_date)
        VALUES (:code, :userId, :media, :reasons, :sessionId, :creationDate, :resendDate, :expirationDate)
        ON CONFLICT (session_id, code) DO NOTHING
        """
    )
    override suspend fun insertUnlessCodeHeld(
        code: String,
        userId: UUID,
        media: String,
        @TypeDef(type = DataType.STRING_ARRAY) reasons: Array<String>,
        sessionId: UUID,
        creationDate: LocalDateTime,
        resendDate: LocalDateTime?,
        expirationDate: LocalDateTime
    ): Int
}
