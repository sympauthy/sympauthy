package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.data.model.UserEntity
import io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mapstruct.factory.Mappers
import java.time.LocalDateTime
import java.util.*

class UserMapperTest {

    private val mapper = Mappers.getMapper(UserMapper::class.java)

    @Test
    fun `toUser - maps all fields`() {
        val id = UUID.randomUUID()
        val creationDate = LocalDateTime.now().minusDays(1)
        val entity = entity(id = id, status = UserStatus.DISABLED.name, creationDate = creationDate)

        val user = mapper.toUser(entity)

        assertEquals(id, user.id)
        assertEquals(UserStatus.DISABLED, user.status)
        assertEquals(creationDate, user.creationDate)
    }

    @Test
    fun `toUser - throws when status is unknown`() {
        val entity = entity(status = "UNKNOWN")

        val exception = assertThrows<BusinessException> {
            mapper.toUser(entity)
        }
        assertEquals("mapper.user.invalid_property", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
    }

    private fun entity(
        id: UUID? = UUID.randomUUID(),
        status: String = UserStatus.ENABLED.name,
        creationDate: LocalDateTime = LocalDateTime.now().minusDays(1),
    ): UserEntity {
        return UserEntity(
            status = status,
            creationDate = creationDate,
            sessionId = null
        ).apply { this.id = id }
    }
}
