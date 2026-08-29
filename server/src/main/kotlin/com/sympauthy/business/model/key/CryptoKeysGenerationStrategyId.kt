package com.sympauthy.business.model.key

/**
 * The strategies a deployment may pick from to have this server generate its cryptographic keys.
 *
 * Each entry carries the qualifier the matching implementation is published under, so the value an
 * operator writes in the configuration is the value that resolves the bean.
 *
 * @see CryptoKeysGenerationStrategyIds for the string constants the implementations are named with.
 */
enum class CryptoKeysGenerationStrategyId(
    val id: String
) {
    AUTO_INCREMENT(CryptoKeysGenerationStrategyIds.AUTO_INCREMENT);

    companion object {
        fun fromIdOrNull(id: String): CryptoKeysGenerationStrategyId? = entries.firstOrNull { it.id == id }
    }
}

object CryptoKeysGenerationStrategyIds {
    const val AUTO_INCREMENT = "autoincrement"
}
