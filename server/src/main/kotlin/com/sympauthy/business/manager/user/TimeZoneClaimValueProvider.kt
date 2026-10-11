package com.sympauthy.business.manager.user

import com.sympauthy.business.model.user.claim.EnabledClaim
import com.sympauthy.business.model.user.claim.TimeZone
import com.sympauthy.util.loggerForClass
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import jakarta.inject.Singleton
import kotlinx.coroutines.rx3.await
import java.time.DateTimeException
import java.time.ZoneId
import java.time.zone.ZoneRulesException

@Singleton
class TimeZoneClaimValueProvider {

    private val logger = loggerForClass()

    /**
     * Map of all [TimeZone] available in the timezone database embedded into the JVM.
     */
    private val cachedAvailableTimeZoneMap = Observable
        .create {
            ZoneId.getAvailableZoneIds().forEach(it::onNext)
            it.onComplete()
        }
        .flatMap { zoneId ->
            try {
                ZoneId.of(zoneId).let { Observable.just(it) }
            } catch (e: DateTimeException) {
                logger.error("Failed to load timezone $zoneId.", e)
                Observable.empty()
            } catch (e: ZoneRulesException) {
                logger.error("Failed to load timezone $zoneId.", e)
                Observable.empty()
            }
        }
        .map(::TimeZone)
        .toList()
        .map { timeZones ->
            timeZones.associateBy { it.zone.id }
        }
        .observeOn(Schedulers.computation())
        .cache()

    /**
     * Return the list of allowed [TimeZone] that can be used by the user for the provided [claim].
     */
    suspend fun listAvailableTimezones(claim: EnabledClaim): List<TimeZone> {
        val availableTimeZoneMap = cachedAvailableTimeZoneMap.await()
        return claim.allowedValues?.mapNotNull {
            availableTimeZoneMap[it]
        } ?: availableTimeZoneMap.values.toList()
    }
}
