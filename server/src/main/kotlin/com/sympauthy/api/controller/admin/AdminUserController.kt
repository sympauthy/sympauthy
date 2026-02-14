package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminUserResourceMapper
import com.sympauthy.api.resource.admin.AdminUserResource
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.security.SecurityRule.IS_ADMIN
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.security.annotation.Secured
import jakarta.inject.Inject
import java.util.*

@Controller("/api/v1/admin/users")
@Secured(IS_ADMIN)
class AdminUserController(
    @Inject private val userManager: UserManager,
    @Inject private val userMapper: AdminUserResourceMapper
) {

    @Get("/{id}")
    suspend fun getUser(
        @PathVariable id: UUID
    ): AdminUserResource {
        val user = userManager.findByIdOrNull(id).orNotFound()
        return userMapper.toResource(user)
    }
}
