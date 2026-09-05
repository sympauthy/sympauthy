package com.sympauthy.business.manager

import jakarta.inject.Singleton
import java.security.SecureRandom
import java.util.*

/**
 * Generate randomized payload to be used across the application.
 */
@Singleton
class RandomGenerator {

    private val secureRandom: SecureRandom = SecureRandom()
    private val hexEncoder = HexFormat.of()

    /**
     * Generate an [Int] between [origin] (inclusive) and [bound] (exclusive).
     */
    fun generateInt(origin: Int, bound: Int): Int {
        return secureRandom.nextInt(origin, bound)
    }

    /**
     * Generate a byte array containing random values.
     */
    fun generate(lengthInBytes: Int = DEFAULT_LENGTH): ByteArray {
        require(lengthInBytes > 0) { "lengthInBytes must be >= 1." }
        val byteArray = ByteArray(lengthInBytes)
        secureRandom.nextBytes(byteArray)
        return byteArray
    }

    /**
     * Generate a byte array containing [lengthInBytes] random bytes then convert the value into hexadecimal.
     */
    fun generateAndEncodeToHex(lengthInBytes: Int = DEFAULT_LENGTH): String {
        return hexEncoder.formatHex(generate(lengthInBytes))
    }

    companion object {
        const val DEFAULT_LENGTH = 8
    }
}
