package com.sympauthy.business.manager.auth

import com.sympauthy.data.model.AuthorizeAttemptEntity
import com.sympauthy.data.repository.AuthorizationCodeRepository
import com.sympauthy.data.repository.AuthorizeAttemptRepository
import com.sympauthy.data.repository.ProviderUserInfoRepository
import com.sympauthy.data.repository.ValidationCodeRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class AuthorizeAttemptCleanerTest {

    @MockK
    lateinit var authorizeAttemptRepository: AuthorizeAttemptRepository

    @MockK
    lateinit var validationCodeRepository: ValidationCodeRepository

    @MockK
    lateinit var authorizationCodeRepository: AuthorizationCodeRepository

    @MockK
    lateinit var providerUserInfoRepository: ProviderUserInfoRepository

    @InjectMockKs
    lateinit var cleaner: AuthorizeAttemptCleaner

    @Test
    fun `clean - deletes expired attempts and their dependencies including provisional provider identities`() =
        runTest {
            val id1 = UUID.randomUUID()
            val id2 = UUID.randomUUID()
            val ids = listOf(id1, id2)
            val expired = listOf(
                mockk<AuthorizeAttemptEntity> { every { id } returns id1 },
                mockk<AuthorizeAttemptEntity> { every { id } returns id2 }
            )

            coEvery { authorizeAttemptRepository.findExpired() } returns expired
            coEvery { authorizationCodeRepository.deleteByAttemptIdIn(ids) } returns 3
            coEvery { validationCodeRepository.deleteByAttemptIdIn(ids) } returns 2
            coEvery { providerUserInfoRepository.deleteByAuthorizeAttemptIdIn(ids) } returns 1
            coEvery { authorizeAttemptRepository.deleteByIds(ids) } returns 2

            val result = cleaner.clean()

            assertEquals(2, result.authorizeAttemptCount)
            assertEquals(3, result.authorizationCodeCount)
            assertEquals(2, result.validationCodesCount)
            assertEquals(1, result.provisionalProviderCount)
        }
}
