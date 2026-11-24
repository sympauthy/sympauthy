package com.sympauthy.business.password.manager

import com.sympauthy.business.validationcode.model.ValidationCode
import java.time.LocalDateTime

/**
 * What tells a person about a reset of their password: the code proving it, the notices answering a reset that
 * sends none, and the notice that one changed the password. Every message goes to an email [address].
 *
 * Depending on the implementation, each method may queue the sending and return immediately. A message queued is
 * written in the transaction the caller runs in, and leaves only once it has committed.
 */
interface PasswordResetMessageSender {

    /**
     * Whether this server can send a message about a reset at all, which is what lets a flow offer one.
     */
    val enabled: Boolean

    /**
     * Send the [validationCode] proving a reset of the password of the account whose proven address is [address].
     */
    suspend fun sendCode(address: String, validationCode: ValidationCode)

    /**
     * Tell [address], which a reset was asked for, that no account uses it and no code was sent.
     */
    suspend fun sendNoAccount(address: String)

    /**
     * Tell the account whose proven address is [address] that it holds no password to reset, and that it signs in
     * through the providers [providerNames] names instead.
     */
    suspend fun sendNoPassword(address: String, providerNames: List<String>)

    /**
     * Tell the account whose proven address is [address] that a reset replaced its password at [changedAt].
     */
    suspend fun sendPasswordChanged(address: String, changedAt: LocalDateTime)
}
