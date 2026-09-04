package com.sympauthy.business.model.filter

import com.sympauthy.business.model.oauth2.ScopeType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ValueFilterTest {

    @Test
    fun `matches - Pass every value when the caller named no criterion`() {
        val filter: ValueFilter<ScopeType> = ValueFilter.Unfiltered

        assertTrue(ScopeType.entries.all(filter::matches))
    }

    @Test
    fun `matches - Pass the named value only`() {
        val filter = ValueFilter.Matching(ScopeType.CONSENTABLE)

        assertTrue(filter.matches(ScopeType.CONSENTABLE))
        assertFalse(filter.matches(ScopeType.GRANTABLE))
    }

    @Test
    fun `matches - Pass no value when the caller named one the set does not hold`() {
        val filter: ValueFilter<ScopeType> = ValueFilter.MatchesNothing

        assertTrue(ScopeType.entries.none(filter::matches))
    }
}
