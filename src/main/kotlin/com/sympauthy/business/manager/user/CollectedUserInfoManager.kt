package com.sympauthy.business.manager.user

import com.sympauthy.business.mapper.CollectedUserInfoMapper
import com.sympauthy.business.mapper.CollectedUserInfoUpdateMapper
import com.sympauthy.business.model.user.CollectedUserInfo
import com.sympauthy.business.model.user.CollectedUserInfoUpdate
import com.sympauthy.business.model.user.User
import com.sympauthy.business.security.Context
import com.sympauthy.data.model.CollectedUserInfoEntity
import com.sympauthy.data.repository.CollectedUserInfoRepository
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import java.util.*

@Singleton
open class CollectedUserInfoManager(
    @Inject private val userInfoRepository: CollectedUserInfoRepository,
    @Inject private val userInfoMapper: CollectedUserInfoMapper,
    @Inject private val userInfoUpdateMapper: CollectedUserInfoUpdateMapper
) {

    /**
     * Return the user info we have collected for the user identified by [userId].
     * Only return the user info that can be read accorded to the [context].
     */
    suspend fun findReadableUserInfoByUserId(
        context: Context,
        userId: UUID
    ): List<CollectedUserInfo> {
        return userInfoRepository.findByUserId(userId)
            .mapNotNull(userInfoMapper::toCollectedUserInfo)
            .filter { context.canRead(it.claim) }
    }

    /**
     * Update the claims collected for the [user] and return all the claims readable according to the [context].
     */
    @Transactional
    open suspend fun updateUserInfo(
        context: Context,
        user: User,
        updates: List<CollectedUserInfoUpdate>
    ): List<CollectedUserInfo> = coroutineScope {
        val applicableUpdates = updates.filter { context.canWrite(it.claim) }
        val existingEntities = userInfoRepository.findByUserId(user.id)
            .associateBy(CollectedUserInfoEntity::claim)
            .toMutableMap()

        val entitiesToDelete = applicableUpdates
            .filter { it.value == null }
            .mapNotNull { existingEntities.remove(it.claim.id) }
        val deferredDelete = async {
            userInfoRepository.deleteAll(entitiesToDelete)
        }

        val entitiesToUpdate = applicableUpdates
            .filter { it.value != null }
            .mapNotNull { update ->
                val entity = existingEntities[update.claim.id]
                entity?.let { update to entity }
            }
            .map { (update, entity) ->
                userInfoUpdateMapper.updateEntity(entity, update).also {
                    existingEntities[update.claim.id] = it
                }
            }

        val entitiesToCreate = applicableUpdates
            .filter { it.value != null }
            .map {
                userInfoUpdateMapper.toEntity(user.id, it)
            }
        val deferredSave = async {
            userInfoRepository.saveAll(entitiesToCreate + entitiesToUpdate)
                .collect()
        }

        awaitAll(deferredSave, deferredDelete)

        (existingEntities.values + entitiesToCreate)
            .mapNotNull { userInfoMapper.toCollectedUserInfo(it) }
            .filter { context.canRead(it.claim) }
    }
}