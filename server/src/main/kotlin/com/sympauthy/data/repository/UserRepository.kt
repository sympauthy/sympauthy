package com.sympauthy.data.repository

import com.sympauthy.data.model.UserEntity
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import kotlinx.coroutines.flow.Flow
import java.util.*

interface UserRepository : CoroutineCrudRepository<UserEntity, UUID> {

    fun findByStatus(status: String): Flow<UserEntity>
}
