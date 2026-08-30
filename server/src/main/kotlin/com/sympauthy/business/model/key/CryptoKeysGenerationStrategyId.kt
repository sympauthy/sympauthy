package com.sympauthy.business.model.key

/**
 * The strategies a deployment may pick from to have this server generate its cryptographic keys.
 *
 * Each entry carries the qualifier the matching implementation is published under, so the value an
 * operator writes in the configuration is the value that resolves the bean. Adding an entry without
 * publishing an implementation under its qualifier fails where the bean is resolved, not here.
 */
enum class CryptoKeysGenerationStrategyId(
    val id: String
) {
    AUTO_INCREMENT(CryptoKeysGenerationStrategyQualifiers.AUTO_INCREMENT);

    companion object {
        fun fromIdOrNull(id: String): CryptoKeysGenerationStrategyId? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The qualifiers the generation strategies are published under.
 *
 * They are constants apart from the enumeration because the annotation naming a bean takes one, and
 * an enum entry cannot supply it.
 */
object CryptoKeysGenerationStrategyQualifiers {
    const val AUTO_INCREMENT = "autoincrement"
}
