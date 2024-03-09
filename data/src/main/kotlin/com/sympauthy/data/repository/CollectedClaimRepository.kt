package com.sympauthy.data.repository

import com.sympauthy.data.model.CollectedClaimEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.kotlin.CoroutineJpaSpecificationExecutor
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.micronaut.data.runtime.criteria.get
import io.micronaut.data.runtime.criteria.where
import kotlinx.coroutines.flow.toList
import java.util.*

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface CollectedClaimRepository : CoroutineCrudRepository<CollectedClaimEntity, UUID>,
    CoroutineJpaSpecificationExecutor<CollectedClaimEntity> {

    suspend fun findByUserId(userId: UUID): List<CollectedClaimEntity>
}

/**
 * Find any claims (whose id is included in the [claimIds]) that matches the [value].
 */
suspend fun CollectedClaimRepository.findAnyClaimMatching(
    claimIds: List<String>,
    value: String
): CollectedClaimEntity? {
    return findOne(where {
        and {
            root[CollectedClaimEntity::value] eq value
            or {
                claimIds.forEach {
                    root[CollectedClaimEntity::claim] eq it
                }
            }
        }
    })
}

/**
 * Find any claims (whose id is included in the [claimIds]) that matches one of the value in [claimValues].
 */
suspend fun CollectedClaimRepository.findAnyClaimMatching(
    claimIds: List<String>,
    claimValues: List<String>,
): List<CollectedClaimEntity> {
    if (claimIds.isEmpty() || claimValues.isEmpty()) {
        return emptyList()
    }
    // Do not understand but the 'in' does not seem to work properly.
    val criteria = where {
        and {
            or {
                claimIds.forEach {
                    root[CollectedClaimEntity::claim] eq it
                }
            }
            or {
                claimValues.forEach {
                    root[CollectedClaimEntity::value] eq it
                }
            }
        }
    }
    return findAll(PredicateSpecification.where(criteria)).toList()
}
