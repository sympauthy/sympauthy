package com.sympauthy.business.manager.mfa

import com.sympauthy.business.manager.RandomGenerator
import com.sympauthy.business.mapper.TotpEnrollmentMapper
import com.sympauthy.business.model.mfa.TotpEnrollment
import com.sympauthy.business.model.user.User
import com.sympauthy.data.model.TotpEnrollmentEntity
import com.sympauthy.data.repository.TotpEnrollmentRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDateTime
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Singleton
class TotpManager(
    @Inject private val randomGenerator: RandomGenerator,
    @Inject private val totpEnrollmentRepository: TotpEnrollmentRepository,
    @Inject private val totpEnrollmentMapper: TotpEnrollmentMapper
) {

    companion object {
        /**
         * TOTP secret length: 20 bytes (160 bits) as recommended by RFC 4226.
         */
        const val SECRET_LENGTH_IN_BYTES = 20

        /**
         * TOTP time step: 30 seconds as defined by RFC 6238.
         */
        const val TIME_STEP_SECONDS = 30L

        /**
         * Number of digits in a TOTP code.
         */
        const val CODE_DIGITS = 6

        /**
         * Number of time steps to accept on each side of the current step to accommodate clock skew.
         */
        const val CLOCK_SKEW_STEPS = 1

        private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        private const val CODE_MODULO = 1_000_000 // 10^CODE_DIGITS
    }

    /**
     * Initiates a new TOTP enrollment for the given [user].
     * Any existing unconfirmed enrollment for the user is deleted first to avoid stale secrets.
     * Returns the persisted unconfirmed [TotpEnrollment].
     */
    suspend fun initiateEnrollment(user: User): TotpEnrollment {
        totpEnrollmentRepository.findByUserIdAndConfirmedDateIsNull(user.id)
            .forEach { totpEnrollmentRepository.delete(it) }

        val entity = TotpEnrollmentEntity(
            userId = user.id,
            secret = randomGenerator.generate(SECRET_LENGTH_IN_BYTES),
            creationDate = LocalDateTime.now(),
            confirmedDate = null
        )
        return totpEnrollmentMapper.toTotpEnrollment(totpEnrollmentRepository.save(entity))
    }

    /**
     * Confirms the [enrollment] by validating the [code] entered by the user.
     * Returns the updated [TotpEnrollment] on success, or null if the code is invalid.
     */
    suspend fun confirmEnrollment(enrollment: TotpEnrollment, code: String): TotpEnrollment? {
        if (!isCodeValid(enrollment.secret, code)) return null
        val confirmedDate = LocalDateTime.now()
        totpEnrollmentRepository.updateConfirmedDate(enrollment.id, confirmedDate)
        return totpEnrollmentRepository.findById(enrollment.id)
            ?.let(totpEnrollmentMapper::toTotpEnrollment)
    }

    /**
     * Returns true if [code] is valid against any confirmed TOTP enrollment for the given [userId].
     */
    suspend fun isCodeValidForUser(userId: UUID, code: String): Boolean {
        return totpEnrollmentRepository.findByUserIdAndConfirmedDateIsNotNull(userId)
            .any { isCodeValid(it.secret, code) }
    }

    /**
     * Returns all confirmed TOTP enrollments for the given [userId].
     */
    suspend fun findConfirmedEnrollments(userId: UUID): List<TotpEnrollment> {
        return totpEnrollmentRepository.findByUserIdAndConfirmedDateIsNotNull(userId)
            .map(totpEnrollmentMapper::toTotpEnrollment)
    }

    /**
     * Builds a standard otpauth:// URI ready for QR code generation.
     *
     * The [issuer] is the name of the service (e.g. the configured application name or domain).
     * The [account] is the user identifier shown in the authenticator app (e.g. the user's email).
     */
    fun buildOtpauthUri(issuer: String, account: String, secret: ByteArray): String {
        val label = "${URLEncoder.encode(issuer, UTF_8)}:${URLEncoder.encode(account, UTF_8)}"
        return "otpauth://totp/$label?secret=${encodeSecretToBase32(secret)}&issuer=${URLEncoder.encode(issuer, UTF_8)}"
    }

    /**
     * Encodes [secret] bytes to a Base32 string (RFC 4648) for display to the user.
     */
    fun encodeSecretToBase32(secret: ByteArray): String {
        val result = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (b in secret) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                result.append(BASE32_ALPHABET[(buffer shr bitsLeft) and 0x1f])
            }
        }
        if (bitsLeft > 0) {
            result.append(BASE32_ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1f])
        }
        while (result.length % 8 != 0) {
            result.append('=')
        }
        return result.toString()
    }

    /**
     * Returns true if [code] is valid for [secret] at the current time,
     * allowing [CLOCK_SKEW_STEPS] time steps of tolerance in both directions.
     */
    internal fun isCodeValid(secret: ByteArray, code: String): Boolean {
        val currentStep = System.currentTimeMillis() / 1000 / TIME_STEP_SECONDS
        return (-CLOCK_SKEW_STEPS..CLOCK_SKEW_STEPS).any { delta ->
            generateCode(secret, currentStep + delta) == code
        }
    }

    /**
     * Generates the TOTP code for the given [secret] and time [step] using HMAC-SHA1 (RFC 6238 / RFC 4226).
     */
    internal fun generateCode(secret: ByteArray, step: Long): String {
        val msg = ByteBuffer.allocate(8).putLong(step).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "HmacSHA1"))
        val hash = mac.doFinal(msg)

        // Dynamic truncation (RFC 4226 §5.4)
        val offset = hash[hash.size - 1].toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)

        return (binary % CODE_MODULO).toString().padStart(CODE_DIGITS, '0')
    }
}
