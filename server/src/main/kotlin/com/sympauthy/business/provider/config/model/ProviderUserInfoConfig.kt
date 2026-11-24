package com.sympauthy.business.provider.config.model

import com.jayway.jsonpath.JsonPath
import com.sympauthy.business.provider.model.ProviderUserInfoPathKey
import java.net.URI

data class ProviderUserInfoConfig(
    val uri: URI,
    val paths: Map<ProviderUserInfoPathKey, JsonPath>
)
