package com.sympauthy.business.model.user.claim

import kotlin.reflect.KClass

/**
 * Enumeration of supported data type for a user claim.
 *
 * The type a deployment declares here decides the form a value of that claim takes everywhere it is
 * published. The type the value happens to be carrying decides nothing: it is an artifact of how the
 * value round-tripped through the object mapper, and a publisher reading the wire form off it
 * publishes whatever that round trip produced.
 */
enum class ClaimDataType(
    /**
     * Primitive type used to exchange the claim between the authorization server and its clients.
     *
     * It is the type a validated value is held in, the type a stored one is read back as, and the
     * JSON type a published one takes.
     *
     * ex: Email are encoded as String.
     */
    val typeClass: KClass<*>,
    /**
     * Whether a claim of this type may be named in `auth.identifier-claims`.
     *
     * A type qualifies when a value of it is something one person is known by and types to sign in. A
     * [NUMBER] is one: an employee number identifies a person. The rest name a property somebody shares
     * with everybody else who has it — a [BOOLEAN] identifier would cap a deployment at two accounts, a
     * [DATE] one at a single account per day, and a [TIMEZONE] one at a single account per zone, which is
     * not an identity but a collision waiting for the second person. See `docs/design/identifier-claims.md`.
     */
    val canIdentify: Boolean = false,
    /**
     * Whether a claim of this type may be collected in the interactive flow, which is a form a person
     * types a value into. A [JSON] document is not one: there is no input it could be typed into.
     */
    val canBeCollectedInFlow: Boolean = true,
    /**
     * Whether a claim of this type may be declared `required`. Required holds up a flow until the person
     * gives a value, so a type the flow cannot collect is a type nobody could ever satisfy it with.
     */
    val canBeRequired: Boolean = canBeCollectedInFlow,
    /**
     * Whether a claim of this type may be placed in a `group`. A group organizes claims when they are
     * presented to a person, and assembles the `address` object out of the members of its own, so a type
     * that is never presented and is no member of that object has nothing for one to do.
     */
    val canBeGrouped: Boolean = canBeCollectedInFlow,
    /**
     * Whether a claim of this type may name a `verified-id` companion. This server verifies that somebody
     * controls an address by sending a code to it, which is a thing a [JSON] document cannot be proved to be.
     */
    val canBeVerified: Boolean = true,
    /**
     * Whether a claim of this type may be restricted to `allowed-values`. An enumeration of [JSON]
     * documents is not something a deployment means.
     */
    val canBeEnumerated: Boolean = true,
    /**
     * Whether a claim of this type may declare a `max-size`, the size in bytes its values are held to,
     * measured on the serialized form a claim stores and publishes.
     *
     * Only a type whose value has an inside is capped. A [JSON] value rides into every response a client
     * reads it through, so a document with no ceiling is a ceiling chosen by whoever writes first.
     */
    val canHaveMaxSize: Boolean = false,
    /**
     * The `max-size` a claim of this type is held to when it declares none, or null where it is then held
     * to no size. Only read where [canHaveMaxSize] is true.
     */
    val defaultMaxSize: Int? = null
) {
    BOOLEAN(Boolean::class),
    DATE(String::class),
    EMAIL(String::class, canIdentify = true),
    /**
     * A JSON object, held and published as one. Never another JSON value: `[1, 2]` and `"foo"` are refused,
     * so the type is a named container for what this server does not model rather than a wildcard
     * overlapping [STRING] and [NUMBER].
     */
    JSON(
        Map::class,
        canBeCollectedInFlow = false,
        canBeVerified = false,
        canBeEnumerated = false,
        canHaveMaxSize = true,
        defaultMaxSize = 8 * 1024
    ),
    NUMBER(Long::class, canIdentify = true),
    PHONE_NUMBER(String::class, canIdentify = true),
    STRING(String::class, canIdentify = true),
    TIMEZONE(String::class)
}
