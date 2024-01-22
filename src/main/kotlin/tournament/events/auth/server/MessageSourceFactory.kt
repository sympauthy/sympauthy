package tournament.events.auth.server

import io.micronaut.context.MessageSource
import io.micronaut.context.annotation.Factory
import io.micronaut.context.i18n.ResourceBundleMessageSource
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import kotlin.annotation.AnnotationRetention.RUNTIME

@Qualifier
@Retention(RUNTIME)
@MustBeDocumented
annotation class DisplayMessages

@Qualifier
@Retention(RUNTIME)
@MustBeDocumented
annotation class ErrorMessages

@Factory
class MessageSourceFactory {

    @Singleton
    @ErrorMessages
    fun provideErrorMessageSource(): MessageSource {
        return ResourceBundleMessageSource("error_messages")
    }

    @Singleton
    @DisplayMessages
    fun provideDisplayMessageSource(): MessageSource {
        return ResourceBundleMessageSource("display_messages")
    }
}
