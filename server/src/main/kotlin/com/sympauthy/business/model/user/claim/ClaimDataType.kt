package com.sympauthy.business.model.user.claim

import kotlin.reflect.KClass

/**
 * Enumeration of supported data type for a user claim.
 */
enum class ClaimDataType(
    /**
     * Primitive type used to exchange the claim between the authorization server and its clients.
     *
     * ex: Email are encoded as String.
     */
    val typeClass: KClass<*>,
    /**
     * Whether a claim of this type may be named in `auth.identifier-claims`.
     *
     * A type qualifies when a value of it is something one person is known by and types to sign in, and
     * when it holds that value in a single spelling — an identifier is compared exactly as it is stored,
     * so a type holding one value in several spellings makes several identities out of one. A [NUMBER] is
     * one: an employee number identifies a person, and it is stored as the [Long] it parses to.
     *
     * Two kinds fail the first half. A [BOOLEAN] identifier would cap a deployment at two accounts and a
     * [DATE] one at a single account per day, which is not an identity but a collision waiting for the
     * second person. [TIMEZONE] fails the second: its parser is case-sensitive and the value is published
     * back, so it can be neither folded nor compared. See `docs/identifier-claims.md`.
     */
    val canIdentify: Boolean = false
) {
    BOOLEAN(String::class),
    DATE(String::class),
    EMAIL(String::class, canIdentify = true),
    NUMBER(Long::class, canIdentify = true),
    PHONE_NUMBER(String::class, canIdentify = true),
    STRING(String::class, canIdentify = true),
    TIMEZONE(String::class)
}
