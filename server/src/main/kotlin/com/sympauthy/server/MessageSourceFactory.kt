package com.sympauthy.server

import com.sympauthy.util.DEFAULT_LOCALE
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
annotation class MailMessages

@Qualifier
@Retention(RUNTIME)
@MustBeDocumented
annotation class ErrorMessages

@Qualifier
@Retention(RUNTIME)
@MustBeDocumented
annotation class AdminMessages

@Factory
class MessageSourceFactory {

    @Singleton
    @ErrorMessages
    fun provideErrorMessageSource(): MessageSource {
        return ResourceBundleMessageSource("error_messages", DEFAULT_LOCALE)
    }

    @Singleton
    @MailMessages
    fun provideMailMessageSource(): MessageSource {
        return ResourceBundleMessageSource("mail_messages", DEFAULT_LOCALE)
    }

    @Singleton
    @DisplayMessages
    fun provideDisplayMessageSource(): MessageSource {
        return ResourceBundleMessageSource("display_messages", DEFAULT_LOCALE)
    }

    @Singleton
    @AdminMessages
    fun provideAdminMessageSource(): MessageSource {
        return ResourceBundleMessageSource("admin_messages", DEFAULT_LOCALE)
    }
}
