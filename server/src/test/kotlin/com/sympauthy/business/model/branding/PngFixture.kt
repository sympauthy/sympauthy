package com.sympauthy.business.model.branding

import java.nio.ByteBuffer

/**
 * The opening of a PNG [width] by [height]: its signature and its header chunk, which is all a reader of its size
 * looks at.
 */
fun pngOf(width: Int, height: Int): ByteArray = ByteBuffer.allocate(33)
    .put(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
    .putInt(13)
    .put("IHDR".toByteArray(Charsets.US_ASCII))
    .putInt(width)
    .putInt(height)
    .put(byteArrayOf(8, 6, 0, 0, 0))
    .putInt(0)
    .array()
