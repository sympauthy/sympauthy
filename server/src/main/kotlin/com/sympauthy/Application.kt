package com.sympauthy

import io.micronaut.runtime.Micronaut.build
import java.util.*

object Application {

    /**
     * The package the server's own classes are named under, which is the one this entry point is in.
     *
     * Read off the class rather than written out, so that renaming the package carries everything
     * reading this with it instead of leaving a string behind that still compiles.
     */
    val PACKAGE: String = Application::class.java.packageName

    @JvmStatic
    fun main(args: Array<String>) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        build()
            .args(*args)
            .packages(PACKAGE)
            .start()
    }
}
