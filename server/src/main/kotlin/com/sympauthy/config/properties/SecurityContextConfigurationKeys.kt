package com.sympauthy.config.properties

import com.sympauthy.config.properties.AdvancedConfigurationProperties.Companion.ADVANCED_KEY

/**
 * The prefix the address and the location settings share.
 *
 * It is declared here rather than in either of them because neither owns it: the domain is one, the
 * settings under it are two, and anchoring the prefix in one child would make renaming that child
 * move the other's keys.
 */
const val SECURITY_CONTEXT_KEY = "$ADVANCED_KEY.security-context"
