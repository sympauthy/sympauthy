package tournament.events.auth.config.factory

import io.micronaut.context.MessageSource
import io.micronaut.context.annotation.Factory
import io.micronaut.context.i18n.ResourceBundleMessageSource
import jakarta.inject.Singleton

@Factory
class MessageSourceFactory {

    @Singleton
    fun provideMessageSource(): MessageSource {
        return ResourceBundleMessageSource("messages")
    }
}
