package com.sympauthy.business.mapper

import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.flow.InteractiveFlowSessionMfaEnrollment
import com.sympauthy.data.model.InteractiveFlowSessionMfaEnrollmentEntity
import org.mapstruct.Mapper

/**
 * Handle the mapping from the [InteractiveFlowSessionMfaEnrollmentEntity] to the
 * [InteractiveFlowSessionMfaEnrollment] business model.
 */
@Mapper(
    config = ToBusinessMapperConfig::class
)
abstract class InteractiveFlowSessionMfaEnrollmentMapper {

    fun toInteractiveFlowSessionMfaEnrollment(
        entity: InteractiveFlowSessionMfaEnrollmentEntity
    ): InteractiveFlowSessionMfaEnrollment {
        return InteractiveFlowSessionMfaEnrollment(
            sessionId = entity.sessionId,
            returnUri = entity.returnUri,
        )
    }
}
