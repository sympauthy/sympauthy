package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.invitation.Invitation
import com.sympauthy.business.model.invitation.InvitationCreatedBy
import com.sympauthy.business.model.invitation.InvitationStatus
import com.sympauthy.data.model.InvitationEntity
import org.mapstruct.Mapper
import java.time.LocalDateTime

@Mapper(config = ToBusinessMapperConfig::class)
abstract class InvitationMapper {

    fun toInvitation(entity: InvitationEntity): Invitation {
        val rawStatus = try {
            InvitationStatus.valueOf(entity.status)
        } catch (_: IllegalArgumentException) {
            throw invalidBusinessException("status")
        }
        val status = when {
            rawStatus == InvitationStatus.PENDING
                    && entity.expiresAt.isBefore(LocalDateTime.now()) -> InvitationStatus.EXPIRED
            else -> rawStatus
        }
        val createdBy = try {
            InvitationCreatedBy.valueOf(entity.createdBy)
        } catch (_: IllegalArgumentException) {
            throw invalidBusinessException("createdBy")
        }
        return Invitation(
            id = entity.id ?: throw invalidBusinessException("id"),
            audienceId = entity.audienceId,
            tokenPrefix = entity.tokenPrefix,
            claims = entity.claims,
            note = entity.note,
            status = status,
            createdBy = createdBy,
            createdById = entity.createdById,
            consumedByUserId = entity.consumedByUserId,
            createdAt = entity.createdAt,
            expiresAt = entity.expiresAt,
            consumedAt = entity.consumedAt,
            revokedAt = entity.revokedAt,
        )
    }

    private fun invalidBusinessException(invalidProperty: String): BusinessException {
        return businessExceptionOf(
            detailsId = "mapper.invitation.invalid_property",
            values = arrayOf("property" to invalidProperty)
        )
    }
}
