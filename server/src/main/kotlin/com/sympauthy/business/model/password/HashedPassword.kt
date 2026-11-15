package com.sympauthy.business.model.password

/**
 * A password as it is stored: the [salt] it was hashed with and the [hash] that came out. The password itself is
 * never kept.
 */
class HashedPassword(
    val salt: ByteArray,
    val hash: ByteArray
)
