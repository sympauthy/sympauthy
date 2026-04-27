package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.ClientTemplatesConfig
import com.sympauthy.config.model.DisabledClientTemplatesConfig
import com.sympauthy.config.model.EnabledClientTemplatesConfig
import com.sympauthy.config.parsing.ClientTemplatesConfigParser
import com.sympauthy.config.properties.ClientTemplateConfigurationProperties
import com.sympauthy.config.validation.ClientTemplatesConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Factory
class ClientTemplatesConfigFactory(
    @Inject private val clientTemplatesParser: ClientTemplatesConfigParser,
    @Inject private val clientTemplatesValidator: ClientTemplatesConfigValidator
) {

    @Singleton
    fun provideClientTemplates(
        templatesList: List<ClientTemplateConfigurationProperties>
    ): Flow<ClientTemplatesConfig> {
        return flow {
            val ctx = ConfigParsingContext()
            val parsed = clientTemplatesParser.parse(ctx, templatesList)
            val templates = clientTemplatesValidator.validate(ctx, parsed)
            val config = if (ctx.hasErrors) DisabledClientTemplatesConfig(ctx.errors)
            else EnabledClientTemplatesConfig(templates)
            emit(config)
        }
    }
}
