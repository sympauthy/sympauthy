package com.sympauthy

import org.junit.platform.launcher.LauncherSession
import org.junit.platform.launcher.LauncherSessionListener
import java.util.TimeZone

/**
 * Forces the default zone to UTC before any test runs, the way [Application] does before the server
 * runs.
 *
 * No test calls `main`, so without this a test JVM keeps the machine's zone: an entity's
 * [java.time.LocalDate] is then written through one offset and read back through another, and a date
 * column round-trips a day out west of Greenwich. Registered as a service so that it applies to
 * whatever starts the JUnit platform — Gradle, an IDE runner, a bare launcher — rather than to one of
 * them.
 */
class UtcTimeZoneListener : LauncherSessionListener {

    override fun launcherSessionOpened(session: LauncherSession) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }
}
