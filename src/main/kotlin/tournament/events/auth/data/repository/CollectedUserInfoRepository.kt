package tournament.events.auth.data.repository

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.jpa.kotlin.CoroutineJpaSpecificationExecutor
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.micronaut.data.runtime.criteria.get
import io.micronaut.data.runtime.criteria.where
import tournament.events.auth.business.model.user.claim.OpenIdClaim
import tournament.events.auth.data.model.CollectedUserInfoEntity
import java.util.*

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface CollectedUserInfoRepository : CoroutineCrudRepository<CollectedUserInfoEntity, UUID>,
    CoroutineJpaSpecificationExecutor<CollectedUserInfoEntity> {

    suspend fun findByUserId(userId: UUID): List<CollectedUserInfoEntity>
}

suspend fun CollectedUserInfoRepository.findByLogin(
    login: String,
    loginClaims: List<OpenIdClaim>
): CollectedUserInfoEntity? {
    return findOne(where {
        and {
            root[CollectedUserInfoEntity::value] eq login
            or {
                loginClaims.forEach {
                    root[CollectedUserInfoEntity::claim] eq it.id
                }
            }
        }
    })
}
