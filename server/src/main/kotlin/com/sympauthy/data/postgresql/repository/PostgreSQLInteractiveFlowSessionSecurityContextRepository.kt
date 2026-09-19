package com.sympauthy.data.postgresql.repository

import com.sympauthy.data.postgresql.DefaultDataSourceIsPostgreSQL
import com.sympauthy.data.repository.InteractiveFlowSessionSecurityContextRepository
import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.Query
import io.micronaut.data.model.query.builder.sql.Dialect.POSTGRES
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import java.time.LocalDateTime
import java.util.*

@Suppress("unused")
@Requires(condition = DefaultDataSourceIsPostgreSQL::class)
@R2dbcRepository(dialect = POSTGRES)
interface PostgreSQLInteractiveFlowSessionSecurityContextRepository :
    InteractiveFlowSessionSecurityContextRepository {

    /**
     * The insert checks the bound itself: a place already held is inserted-then-bumped through the
     * conflict, and a new one is selected only while the session holds fewer than `maxPlaces` — so the
     * statement answers zero for exactly the case the caller rolls a place out for.
     */
    @Query(
        """
        INSERT INTO interactive_flow_session_security_context
            (session_id, fingerprint, ip, user_agent, country_code, region_code, region, city, time_zone,
             first_seen_date, last_seen_date)
        SELECT :sessionId, :fingerprint, :ip, :userAgent, :countryCode, :regionCode, :region, :city,
               :timeZone, :observedDate, :observedDate
        WHERE EXISTS (
                SELECT 1 FROM interactive_flow_session_security_context
                WHERE session_id = :sessionId AND fingerprint = :fingerprint
              )
           OR (
                SELECT count(*) FROM interactive_flow_session_security_context
                WHERE session_id = :sessionId
              ) < :maxPlaces
        ON CONFLICT (session_id, fingerprint) DO UPDATE SET
            last_seen_date = :observedDate,
            observation_count = interactive_flow_session_security_context.observation_count + 1,
            country_code = :countryCode,
            region_code = :regionCode,
            region = :region,
            city = :city,
            time_zone = :timeZone
        """
    )
    override suspend fun observe(
        sessionId: UUID,
        fingerprint: String,
        ip: String,
        userAgent: String?,
        countryCode: String?,
        regionCode: String?,
        region: String?,
        city: String?,
        timeZone: String?,
        observedDate: LocalDateTime,
        maxPlaces: Int
    ): Int
}
