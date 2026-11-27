package com.sympauthy.business.key.manager

import com.sympauthy.business.key.mapper.CryptoKeysMapper
import com.sympauthy.business.key.mapper.IndexedCryptoKeysMapper
import com.sympauthy.business.key.model.KeyAlgorithm
import com.sympauthy.data.model.CryptoKeysEntity
import com.sympauthy.data.model.IndexedCryptoKeysEntity
import com.sympauthy.data.repository.CryptoKeysRepository
import com.sympauthy.data.repository.IndexedCryptoKeysRepository
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class AutoIncrementCryptoKeysGenerationStrategyTest {

    @MockK
    lateinit var keysRepository: CryptoKeysRepository

    @MockK
    lateinit var indexedKeysRepository: IndexedCryptoKeysRepository

    lateinit var strategy: AutoIncrementCryptoKeysGenerationStrategy

    @BeforeEach
    fun setUp() {
        strategy = AutoIncrementCryptoKeysGenerationStrategy(
            keysRepository = keysRepository,
            indexedKeysRepository = indexedKeysRepository,
            keysMapper = CryptoKeysMapper(),
            indexedKeysMapper = IndexedCryptoKeysMapper()
        )
    }

    @Test
    fun `generateKeys - Date both rows of the key it generated with one instant`() = runTest {
        val name = "auto-increment-crypto-keys-generation-strategy-test"
        val indexedEntity = slot<IndexedCryptoKeysEntity>()
        val entity = slot<CryptoKeysEntity>()

        coEvery { indexedKeysRepository.findByNameAndAlgorithm(name, KeyAlgorithm.HMAC.name) } answers {
            listOfNotNull(if (indexedEntity.isCaptured) indexedEntity.captured else null)
        }
        coEvery { indexedKeysRepository.save(capture(indexedEntity)) } answers {
            indexedEntity.captured.apply { index = 1 }
        }
        coEvery { keysRepository.save(capture(entity)) } answers { entity.captured }

        strategy.generateKeys(name, KeyAlgorithm.HMAC)

        assertEquals(indexedEntity.captured.creationDate, entity.captured.creationDate)
    }
}
