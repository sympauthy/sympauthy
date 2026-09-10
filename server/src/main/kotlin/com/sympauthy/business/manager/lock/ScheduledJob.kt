package com.sympauthy.business.manager.lock

import java.time.Duration
import java.time.Duration.ofMinutes

/**
 * A job this server runs on a schedule, and that one instance at a time should be enough to run.
 *
 * Every value has a row in `job_leases`, seeded by the migration that created the table;
 * `JobLeaseRepositoryTest` holds the two sets equal, because a job whose row is missing takes no lease
 * and therefore never runs at all. Adding a value here means adding its row in the same change.
 */
enum class ScheduledJob(
    val leaseDuration: Duration
) {

    CLEAN_EXPIRED_INTERACTIVE_FLOW_SESSIONS(ofMinutes(10)),

    CLEAN_ABANDONED_ACCOUNTS(ofMinutes(10));

    /** The `job_leases` row this job takes, spelled the way the migration seeded it. */
    val leaseName: String
        get() = name.lowercase()
}
