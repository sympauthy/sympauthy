package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.securitycontext.AccessReviewDecision
import com.sympauthy.business.model.securitycontext.SecurityContext
import com.sympauthy.business.model.securitycontext.SecurityContextGeo
import com.sympauthy.data.model.SecurityContextEntity
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(
    config = ToBusinessMapperConfig::class
)
abstract class SecurityContextMapper {

    @Mapping(target = "geo", source = "entity")
    abstract fun toSecurityContext(entity: SecurityContextEntity): SecurityContext

    abstract fun toGeo(entity: SecurityContextEntity): SecurityContextGeo

    /**
     * The decision [value] names, or null where the place has never been reviewed.
     *
     * A value [AccessReviewDecision] does not name throws an internal [BusinessException]
     * "mapper.security_context.invalid_property": a row this server wrote and cannot read back is its
     * own failure rather than the caller's.
     */
    fun toAccessReviewDecision(value: String?): AccessReviewDecision? = value?.let {
        try {
            AccessReviewDecision.valueOf(it)
        } catch (_: IllegalArgumentException) {
            throw internalBusinessExceptionOf(
                detailsId = "mapper.security_context.invalid_property",
                values = arrayOf("property" to "lastDecision")
            )
        }
    }
}
