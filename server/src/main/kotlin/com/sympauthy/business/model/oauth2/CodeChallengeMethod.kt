package com.sympauthy.business.model.oauth2

enum class CodeChallengeMethod(val value: String) {
    S256("S256");

    companion object {
        fun fromValueOrNull(value: String?): CodeChallengeMethod? =
            value?.let { v -> entries.firstOrNull { it.value == v } }
    }
}
