package com.sympauthy.business.model.client

/**
 * A client as a record names it: the identifier the record holds, with the display name this deployment
 * gives that client now.
 *
 * A grant, a consent and an interactive flow session outlive the client they name, since configuration is
 * not a table. One the configuration no longer declares is still named, by its [id] alone, as is one the
 * configuration declares without a display name.
 */
data class ClientReference(
    val id: String,
    val displayName: String?
)
