package com.sympauthy.data.h2.repository

import com.sympauthy.data.h2.DefaultDataSourceIsH2
import com.sympauthy.data.repository.InteractiveFlowSessionSecurityContextRepository
import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.Query
import io.micronaut.data.model.query.builder.sql.Dialect.H2
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import java.time.LocalDateTime
import java.util.*

@Suppress("unused")
@Requires(condition = DefaultDataSourceIsH2::class)
@R2dbcRepository(dialect = H2)
interface H2InteractiveFlowSessionSecurityContextRepository : InteractiveFlowSessionSecurityContextRepository {

    /**
     * The cap is a predicate on the merge's source rather than on its branches: a source selecting no row
     * matches nothing and inserts nothing, which is how a session at the cap goes on bumping the places it
     * already holds while a new one moves zero rows.
     *
     * Every value is cast rather than bound bare: H2 derives the source's column types from the statement
     * alone, and a parameter it was given no type for is refused before any of it runs.
     *
     * `readOnly = false` because Micronaut Data reads the operation off the statement's first keyword and
     * has no case for `MERGE`, so without it this would be wired up as a query and never run as a write.
     */
    @Query(
        """
        MERGE INTO interactive_flow_session_security_context AS context
        USING (
            SELECT CAST(:sessionId AS uuid) AS session_id, CAST(:fingerprint AS text) AS fingerprint,
                   CAST(:ip AS text) AS ip, CAST(:userAgent AS text) AS user_agent,
                   CAST(:countryCode AS text) AS country_code, CAST(:regionCode AS text) AS region_code,
                   CAST(:region AS text) AS region, CAST(:city AS text) AS city,
                   CAST(:timeZone AS text) AS time_zone, CAST(:observedDate AS timestamp) AS observed_date
            WHERE EXISTS (
                    SELECT 1 FROM interactive_flow_session_security_context
                    WHERE session_id = :sessionId AND fingerprint = :fingerprint
                  )
               OR (
                    SELECT count(*) FROM interactive_flow_session_security_context
                    WHERE session_id = :sessionId
                  ) < :maxPlaces
        ) AS observation
        ON context.session_id = observation.session_id AND context.fingerprint = observation.fingerprint
        WHEN MATCHED THEN UPDATE SET
            context.last_seen_date = observation.observed_date,
            context.observation_count = context.observation_count + 1,
            context.country_code = observation.country_code,
            context.region_code = observation.region_code,
            context.region = observation.region,
            context.city = observation.city,
            context.time_zone = observation.time_zone
        WHEN NOT MATCHED THEN INSERT
            (session_id, fingerprint, ip, user_agent, country_code, region_code, region, city, time_zone,
             first_seen_date, last_seen_date)
            VALUES (observation.session_id, observation.fingerprint, observation.ip,
                    observation.user_agent, observation.country_code, observation.region_code,
                    observation.region, observation.city, observation.time_zone,
                    observation.observed_date, observation.observed_date)
        """,
        readOnly = false
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
