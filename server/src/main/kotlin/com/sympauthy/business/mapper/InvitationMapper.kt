package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.invitation.Invitation
import com.sympauthy.business.model.invitation.InvitationCreatedBy
import com.sympauthy.business.model.invitation.InvitationStatus
import com.sympauthy.data.model.InvitationEntity
import org.mapstruct.Mapper
import java.time.LocalDateTime

/**
 * Handle the mapping from the [InvitationEntity] to the [Invitation] business model.
 *
 * If the row holds a status or a creator the model's enums do not name, or no identifier, an internal
 * [BusinessException] "mapper.invitation.invalid_property" is thrown: a row this server wrote and
 * cannot read back is its own failure rather than the caller's.
 */
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
        return internalBusinessExceptionOf(
            detailsId = "mapper.invitation.invalid_property",
            values = arrayOf("property" to invalidProperty)
        )
    }
}
