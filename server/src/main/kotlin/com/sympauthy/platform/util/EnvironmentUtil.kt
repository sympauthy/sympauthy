package com.sympauthy.platform.util

import io.micronaut.context.env.Environment

const val DEFAULT_ENVIRONMENT = "default"

val Environment.isDefaultActive: Boolean
    get() = this.activeNames.contains(DEFAULT_ENVIRONMENT)
