package com.sympauthy.business.model.branding

import java.security.MessageDigest

/**
 * What is answered under `/branding`, and the tag it is revalidated by: computed from its content, so that two
 * instances holding the same bytes answer the same one.
 */
class BrandAsset(
    val content: ByteArray,
    val mediaType: String
) {
    val etag: String = "\"${MessageDigest.getInstance("SHA-256").digest(content).toHexString()}\""
}
