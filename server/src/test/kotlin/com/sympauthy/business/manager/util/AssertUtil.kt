package com.sympauthy.business.manager.util

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.exception.LocalizedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows

fun assertThrowsLocalizedException(
    detailsId: String,
    executable: () -> Unit
): LocalizedException {
    val exception = assertThrows<LocalizedException> {
        executable()
    }
    assertEquals(detailsId, exception.detailsId)
    return exception
}

suspend fun coAssertThrowsBusinessException(
    detailsId: String,
    executable: suspend () -> Unit
) {
    val exception = assertThrows<BusinessException> {
        executable()
    }
    assertEquals(detailsId, exception.detailsId)
}
