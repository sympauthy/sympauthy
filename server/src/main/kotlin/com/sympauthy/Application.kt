package com.sympauthy

import io.micronaut.runtime.Micronaut.build
import java.util.*

object Application {

    @JvmStatic
    fun main(args: Array<String>) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        build()
            .args(*args)
            .packages("com.sympauthy")
            .start()
    }
}
