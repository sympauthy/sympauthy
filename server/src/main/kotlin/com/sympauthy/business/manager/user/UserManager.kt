package com.sympauthy.business.manager.user

import com.sympauthy.business.mapper.UserMapper
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.model.user.claim.OpenIdClaim.EMAIL
import com.sympauthy.data.model.UserEntity
import com.sympauthy.data.repository.CollectedClaimRepository
import com.sympauthy.data.repository.UserRepository
import com.sympauthy.data.repository.findAnyClaimMatching
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime.now

@Singleton
open class UserManager(
    @Inject private val collectedClaimRepository: CollectedClaimRepository,
    @Inject private val userRepository: UserRepository,
    @Inject private val userMapper: UserMapper
) {

    /**
     * Find the end-user with a collected email claim matching the [email].
     */
    internal suspend fun findByEmail(email: String): User? {
        val userInfo = collectedClaimRepository.findAnyClaimMatching(listOf(EMAIL.id), email)
        return userInfo?.userId
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
        userRepository.save(entity)

        return userMapper.toUser(entity)
    }
}

data class CreateOrAssociateResult(
    val created: Boolean,
    val user: User
)
