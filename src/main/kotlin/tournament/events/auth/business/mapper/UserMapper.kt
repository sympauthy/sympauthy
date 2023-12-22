package tournament.events.auth.business.mapper

import org.mapstruct.Mapper
import tournament.events.auth.business.mapper.config.BusinessMapperConfig
import tournament.events.auth.business.model.user.User
import tournament.events.auth.data.model.UserEntity

@Mapper(
    config = BusinessMapperConfig::class
)
interface UserMapper {

    fun toUser(entity: UserEntity): User
}