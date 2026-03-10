package com.sympauthy.business.manager.user

import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.mapper.ClaimValueMapper
import com.sympauthy.business.mapper.UserMapper
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.data.model.UserEntity
import com.sympauthy.data.repository.CollectedClaimRepository
import com.sympauthy.data.repository.UserRepository
import com.sympauthy.data.repository.findUserIdsMatchingAllClaims
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime.now
import java.util.*

@Singleton
open class UserManager(
    @Inject private val collectedClaimRepository: CollectedClaimRepository,
    @Inject private val userRepository: UserRepository,
    @Inject private val userMapper: UserMapper
) {

    @Inject
    private lateinit var claimValueMapper: ClaimValueMapper

    /**
     * Find the end-user identified by [id]. Otherwise, return null.
     */
    suspend fun findByIdOrNull(id: UUID?): User? {
        return id?.let { userRepository.findById(it) }
            ?.let(userMapper::toUser)
    }

    /**
     * Find the end-user identified by [id]. Otherwise, throws an unrecoverable business exception.
     */
    suspend fun findById(id: UUID?): User {
        return findByIdOrNull(id) ?: throw businessExceptionOf(
            detailsId = "user.not_found",
            "userId" to "$id"
        )
    }

    /**
     * Find an end-user whose collected claims match ALL entries in [claimValues].
     * Returns the first matching user, or null if none found.
     */
    suspend fun findByIdentifierClaims(claimValues: Map<String, String>): User? {
        val entityClaimValues = claimValues.mapValues { entry -> claimValueMapper.toEntity(entry.value) }
        val userIds = collectedClaimRepository.findUserIdsMatchingAllClaims(entityClaimValues)
        return userIds.firstOrNull()
            ?.let { userRepository.findById(it) }
            ?.let(userMapper::toUser)
    }

    /**
     * Create a new [User].
     */
    @Transactional
    internal open suspend fun createUser(): User {
        val entity = UserEntity(
            status = UserStatus.ENABLED.name,
            creationDate = now()
        )
        val savedEntity = userRepository.save(entity)
        return userMapper.toUser(savedEntity)
    }
}

data class CreateOrAssociateResult(
    val created: Boolean,
    val user: User
)
