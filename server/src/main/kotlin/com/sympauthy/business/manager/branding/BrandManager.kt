package com.sympauthy.business.manager.branding

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.model.branding.Brand
import com.sympauthy.business.model.branding.BrandAsset
import com.sympauthy.business.model.branding.BrandImage
import com.sympauthy.business.model.branding.BrandImageName
import com.sympauthy.business.model.branding.BrandImageName.ICON
import com.sympauthy.business.model.branding.BrandImageName.LOGO
import com.sympauthy.business.model.branding.BrandImageName.LOGO_DARK
import com.sympauthy.business.model.branding.DarkMode
import com.sympauthy.client.branding.BrandImageClient
import com.sympauthy.client.remoteErrorType
import com.sympauthy.client.remoteOutcome
import com.sympauthy.config.model.ConfiguredBrandImage
import com.sympauthy.config.model.EnabledUIConfig
import com.sympauthy.config.model.LocalBrandImage
import com.sympauthy.config.model.RemoteBrandImage
import com.sympauthy.config.model.UIConfig
import com.sympauthy.monitoring.Condition
import com.sympauthy.monitoring.ConditionMonitor
import com.sympauthy.monitoring.RemoteCallMonitor
import com.sympauthy.monitoring.loggerForClass
import com.sympauthy.monitoring.telemetry.Outcome
import com.sympauthy.util.configName
import io.micronaut.context.annotation.Context
import io.micronaut.core.io.ResourceResolver
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.exceptions.HttpClientResponseException
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import jakarta.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URI
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull

/**
 * The deployment's brand, as `/branding` answers it. `docs/design/branding.md` is the design this implements.
 *
 * A configuration that failed reads as one that configured nothing: it already takes readiness down, and the
 * sign-in pages are better served with SympAuthy's brand than with none.
 *
 * It is created with the context rather than by the first request, since an image host that does not answer is
 * a condition `/health` has to report from the start.
 */
@Context
class BrandManager(
    uncheckedUIConfig: UIConfig,
    @Inject private val brandImageClient: BrandImageClient,
    @Inject private val remoteCallMonitor: RemoteCallMonitor,
    @Inject private val resourceResolver: ResourceResolver,
    stylesheetRenderer: BrandStylesheetRenderer
) {

    /**
     * The scope an image is fetched on, and retried on while its host does not answer.
     */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val uiConfig = uncheckedUIConfig as? EnabledUIConfig

    private val defaultImages = BrandImageName.entries.associateWith(::readDefaultImage)

    /**
     * SympAuthy's logos as PNG, for the mails: a mail client does not draw an SVG.
     */
    private val defaultMailImages = MAIL_IMAGES.associateWith(::readDefaultMailImage)

    private val resolvedImages = ConcurrentHashMap<BrandImageName, BrandAsset>()

    private val logger = loggerForClass()

    /**
     * The stylesheet both apps link.
     */
    val stylesheet = BrandAsset(
        stylesheetRenderer.render().toByteArray(),
        MediaType.TEXT_CSS
    )

    val brand = Brand(
        displayName = uiConfig?.displayName,
        darkMode = uiConfig?.theme?.darkMode ?: DarkMode.AUTO
    )

    @PostConstruct
    fun init() {
        val config = uiConfig ?: return
        resolve(ICON, config.icon)
        resolve(LOGO, config.logo)
        resolve(LOGO_DARK, config.logoDark)
    }

    @PreDestroy
    fun close() {
        scope.cancel()
    }

    /**
     * What is answered for [name]: the image the deployment configured once it has resolved, and until then
     * what would be answered if it were not configured.
     *
     * Without a dark logo of its own a deployment's dark grounds show its logo, so that its logo does not turn into
     * SympAuthy's when a page turns dark.
     */
    fun getImage(name: BrandImageName): BrandAsset {
        val fallback = if (name == LOGO_DARK) resolvedImages[LOGO] else null
        return resolvedImages[name] ?: fallback ?: defaultImages.getValue(name)
    }

    /**
     * What a mail shows for [name], which is [LOGO] or [LOGO_DARK]: a mail client draws a PNG but not an SVG.
     *
     * The deployment's image where it is a PNG, SympAuthy's PNG where the deployment configured none, and null
     * where it configured an SVG or an image still being fetched, so that a mail shows the deployment's name rather
     * than SympAuthy's logo. Without a dark logo of its own, a deployment's dark grounds show its logo, as on the
     * pages.
     */
    fun getMailImage(name: BrandImageName): BrandAsset? {
        require(name in MAIL_IMAGES) { "Mails show no ${name.configName}." }
        val configured = uiConfig?.let { if (name == LOGO_DARK) it.logoDark ?: it.logo else it.logo }
        val resolved = resolvedImages[name] ?: if (name == LOGO_DARK) resolvedImages[LOGO] else null
        return when {
            configured == null -> defaultMailImages.getValue(name)
            resolved?.mediaType == MediaType.IMAGE_PNG -> resolved
            else -> null
        }
    }

    private fun resolve(name: BrandImageName, image: ConfiguredBrandImage?) {
        when (image) {
            null -> Unit
            is LocalBrandImage -> resolvedImages[name] = image.image.toAsset()
            is RemoteBrandImage -> scope.launch { fetchUntilResolved(name, image.uri) }
        }
    }

    /**
     * Fetch the image every [FETCH_RETRY_INTERVAL] until its host answers, each attempt reported to its
     * condition. A host answering with something that is not an image answered all the same, so its condition
     * recovers, and the image stays unresolved with one ERROR until the server restarts.
     */
    internal suspend fun fetchUntilResolved(name: BrandImageName, uri: URI) {
        while (true) {
            try {
                resolvedImages[name] = fetch(name, uri).toAsset()
                return
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (e.fetchOutcome() !is Outcome.Error) {
                    logger.error(e) {
                        "Image '${name.configName}' answered with something other than an SVG or a PNG within " +
                            "${BrandImage.MAX_SIZE} bytes, and the image of a deployment that did not configure " +
                            "it is answered until the server restarts."
                    }
                    return
                }
            }
            delay(FETCH_RETRY_INTERVAL.toMillis())
        }
    }

    private suspend fun fetch(name: BrandImageName, uri: URI): BrandImage =
        remoteCallMonitor.observe(Condition.BrandImage(name.configName), FETCH_OPERATION, Throwable::fetchOutcome) {
            brandImageClient.fetchImage(uri)?.let(BrandImage::ofOrNull)
                ?: throw internalBusinessExceptionOf("branding.image.unusable", "image" to name.configName)
        }

    private fun readDefaultImage(name: BrandImageName): BrandAsset {
        val resource = "classpath:branding/${name.configName}.svg"
        val content = resourceResolver.getResourceAsStream(resource).getOrNull()?.use { it.readAllBytes() }
            ?: error("$resource is not in this build.")
        val image = BrandImage.ofOrNull(content) ?: error("$resource is not an image this server would answer.")
        return image.toAsset()
    }

    private fun readDefaultMailImage(name: BrandImageName): BrandAsset {
        val resource = "classpath:branding/${name.configName}.png"
        val content = resourceResolver.getResourceAsStream(resource).getOrNull()?.use { it.readAllBytes() }
            ?: error("$resource is not in this build.")
        val image = BrandImage.ofOrNull(content) ?: error("$resource is not an image this server would answer.")
        return image.toAsset()
    }

    private fun BrandImage.toAsset() = BrandAsset(content, type.mediaType)

    private companion object {
        const val FETCH_OPERATION = "fetch"

        /**
         * The images a mail shows.
         */
        val MAIL_IMAGES = setOf(LOGO, LOGO_DARK)

        /**
         * It reuses the conditions' quiet period, as a provider's discovery does.
         */
        val FETCH_RETRY_INTERVAL: Duration = ConditionMonitor.QUIET_PERIOD
    }
}

/**
 * How a fetch that ended in this failure is recorded, and whether it is tried again. Unlike [remoteOutcome], it
 * holds a `408` and a `429` as outages: they say the host will not answer yet rather than what is wrong with the
 * request, and one fetch at startup is all this image gets until a restart.
 */
private fun Throwable.fetchOutcome(): Outcome {
    val response = generateSequence(this) { it.cause }.filterIsInstance<HttpClientResponseException>().firstOrNull()
    return when (response?.status) {
        HttpStatus.REQUEST_TIMEOUT, HttpStatus.TOO_MANY_REQUESTS -> Outcome.Error(remoteErrorType())
        else -> remoteOutcome()
    }
}
