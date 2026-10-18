package com.sympauthy.data.model

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

@Serdeable
@MappedEntity("authorization_codes")
data class AuthorizationCodeEntity(
    @get:Id val sessionId: UUID,
    val code: String,
    /**
     * Identifier of the grant the authorization opened before handing out this code, which every token its
     * exchange issues stands under. A second exchange of the code revokes it.
     */
    val grantId: UUID,
    val creationDate: LocalDateTime,
    val expirationDate: LocalDateTime,
    /**
     * Date and time at which a client exchanged this code for tokens, null until one has. An exchanged
     * code is kept until its session is collected — see `docs/design/security.md`.
     */
    val exchangeDate: LocalDateTime? = null
)
