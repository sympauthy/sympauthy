package com.sympauthy.data.h2.repository

import com.sympauthy.data.h2.DefaultDataSourceIsH2
import com.sympauthy.data.repository.ValidationCodeRepository
import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.model.DataType
import io.micronaut.data.model.query.builder.sql.Dialect.H2
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import java.time.LocalDateTime
import java.util.*

@Suppress("unused")
@Requires(condition = DefaultDataSourceIsH2::class)
@R2dbcRepository(dialect = H2)
interface H2ValidationCodeRepository : ValidationCodeRepository {

    /**
     * The source's columns are cast rather than bound bare: H2 derives their types from the statement alone,
     * and a parameter it was given no type for is refused before any of it runs.
     *
     * `readOnly = false` because Micronaut Data reads the operation off the statement's first keyword and
     * has no case for `MERGE`, so without it this would be wired up as a query and never run as a write.
     */
    @Query(
        """
        MERGE INTO validation_codes AS held
        USING (
            SELECT CAST(:sessionId AS uuid) AS session_id, CAST(:code AS text) AS code
        ) AS drawn
        ON held.session_id = drawn.session_id AND held.code = drawn.code
        WHEN NOT MATCHED THEN INSERT
            (code, user_id, media, reasons, session_id, creation_date, resend_date, expiration_date)
            VALUES (drawn.code, :userId, :media, :reasons, drawn.session_id, :creationDate, :resendDate,
                    :expirationDate)
        """,
        readOnly = false
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
