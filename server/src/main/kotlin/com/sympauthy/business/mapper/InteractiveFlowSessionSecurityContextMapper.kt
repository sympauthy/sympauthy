package com.sympauthy.business.mapper

import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.flow.InteractiveFlowSessionSecurityContext
import com.sympauthy.data.model.InteractiveFlowSessionSecurityContextEntity
import org.mapstruct.Mapper

/**
 * Handle the mapping from the [InteractiveFlowSessionSecurityContextEntity] to the
 * [InteractiveFlowSessionSecurityContext] business model.
 *
 * The row's `fingerprint` is deliberately left behind: it is the key the fold deduplicates on and it belongs
 * to that write path, not to anything reading the observation back.
 */
@Mapper(
    config = ToBusinessMapperConfig::class
)
abstract class InteractiveFlowSessionSecurityContextMapper {

    fun toInteractiveFlowSessionSecurityContext(
        entity: InteractiveFlowSessionSecurityContextEntity
    ): InteractiveFlowSessionSecurityContext {
        return InteractiveFlowSessionSecurityContext(
            sessionId = entity.sessionId,
            ip = entity.ip,
            userAgent = entity.userAgent,
            countryCode = entity.countryCode,
            regionCode = entity.regionCode,
            region = entity.region,
            city = entity.city,
            timeZone = entity.timeZone,
            observedDate = entity.observedDate,
        )
    }
}
