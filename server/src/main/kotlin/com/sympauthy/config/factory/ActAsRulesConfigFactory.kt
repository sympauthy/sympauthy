package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.ActAsRulesConfig
import com.sympauthy.config.model.DisabledActAsRulesConfig
import com.sympauthy.config.model.EnabledActAsRulesConfig
import com.sympauthy.config.parsing.ActAsRulesConfigParser
import com.sympauthy.config.properties.ActAsRuleConfigurationProperties
import com.sympauthy.config.validation.ActAsRulesConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Factory
class ActAsRulesConfigFactory(
    @Inject private val rulesParser: ActAsRulesConfigParser,
    @Inject private val rulesValidator: ActAsRulesConfigValidator
) {

    @Singleton
    fun provideActAsRules(
        actAsPropertiesList: List<ActAsRuleConfigurationProperties>
    ): Flow<ActAsRulesConfig> {
        return flow {
            val ctx = ConfigParsingContext()

            val parsedActAsRules = rulesParser.parse(
                ctx, actAsPropertiesList, ActAsRuleConfigurationProperties.RULES_KEY
            )

            val actAsRules = rulesValidator.validateActAsRules(ctx, parsedActAsRules)

            if (ctx.hasErrors) {
                emit(DisabledActAsRulesConfig(ctx.errors))
            } else {
                emit(EnabledActAsRulesConfig(actAsRules))
            }
        }
    }
}
