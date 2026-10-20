package com.sympauthy.config.model

import java.time.Duration

data class InteractiveFlowConfig(
    /**
     * How long a person has to get through an interactive flow session — signing in, the claims collected,
     * a second factor — from when it starts, whatever purpose started it. A session past it is expired, and
     * collected.
     */
    val expiration: Duration
)
