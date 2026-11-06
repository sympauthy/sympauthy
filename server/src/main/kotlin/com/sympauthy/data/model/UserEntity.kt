package com.sympauthy.data.model

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

@Serdeable
@MappedEntity("users")
data class UserEntity(
    val status: String,
    val creationDate: LocalDateTime,
    override val sessionId: UUID?,
    /**
     * When this person last proved a credential in a flow that went on to complete. Null until the first one,
     * and it outlives the session and the grant it came from.
     * See [com.sympauthy.data.repository.UserRepository.advanceLastAuthenticationDate].
     */
    val lastAuthenticationDate: LocalDateTime? = null,
    /**
     * When a grant held for this person was last refreshed. Null until the first refresh, and it outlives the
     * grant. See [com.sympauthy.data.repository.UserRepository.advanceLastRefreshDate].
     */
    val lastRefreshDate: LocalDateTime? = null
) : SessionScoped {
    @Id
    @GeneratedValue
    var id: UUID? = null
}

