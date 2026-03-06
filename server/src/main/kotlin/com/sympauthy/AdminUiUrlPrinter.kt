package com.sympauthy

import com.sympauthy.config.model.AdminConfig
import com.sympauthy.config.model.EnabledAdminConfig
import com.sympauthy.config.model.EnabledUrlsConfig
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.util.loggerForClass
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.discovery.event.ServiceReadyEvent
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class AdminUiUrlPrinter(
    @Inject private val adminConfig: AdminConfig,
    @Inject private val urlsConfig: UrlsConfig
) : ApplicationEventListener<ServiceReadyEvent> {

    private val logger = loggerForClass()

    override fun onApplicationEvent(event: ServiceReadyEvent) {
        val config = adminConfig as? EnabledAdminConfig ?: return
        if (!config.enabled || !config.integratedUi) return

        val urls = urlsConfig as? EnabledUrlsConfig ?: return
        logger.info("Admin UI available at ${urls.root}/admin")
    }
}
