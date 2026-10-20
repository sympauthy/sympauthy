package com.sympauthy.data.model

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

@Serdeable
@MappedEntity("authorization_codes")
data class AuthorizationCodeEntity(
    /**
     * Identifier of the grant the authorization opened before handing out this code, which every token its
     * exchange issues stands under, and the key that gives a grant one code only. A second exchange of the
     * code revokes it.
     */
    @get:Id val grantId: UUID,
    val code: String,
    /**
     * The PKCE challenge of the authorization request, copied from it as the code is handed out so that an
     * exchange reads it off the code rather than off the session.
     */
    val codeChallenge: String,
    /**
     * The method that derived [codeChallenge], as `CodeChallengeMethod.value` spells it: `S256`.
     */
    val codeChallengeMethod: String,
    /**
     * The `nonce` of the authorization request, which the id token the exchange issues repeats. Null where
     * the request carried none.
     */
    val nonce: String?,
    val creationDate: LocalDateTime,
    /**
     * Date and time after which the code can no longer be exchanged, `auth.authorization-code.expiration`
     * after [creationDate]. A code is collected then, exchanged or not.
     */
    val expirationDate: LocalDateTime,
    /**
     * Date and time at which a client exchanged this code for tokens, null until one has. An exchanged
     * code is kept until it expires — see `docs/design/oauth2-authorization.md`.
     */
    val exchangeDate: LocalDateTime? = null
)
