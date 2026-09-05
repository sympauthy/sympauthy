package com.sympauthy.business.model.key

/**
 * The strategies a deployment may pick from to have this server generate its cryptographic keys.
 */
enum class CryptoKeysGenerationStrategyId {
    AUTO_INCREMENT
}

/**
 * The qualifiers the generation strategies are published under, which are the names
 * [CryptoKeysGenerationStrategyId] is configured with.
 *
 * They are written out as constants because the annotation naming a bean takes one, and neither an
 * enum entry nor the function spelling its configured name can supply it.
 */
object CryptoKeysGenerationStrategyQualifiers {
    const val AUTO_INCREMENT = "auto-increment"
}
