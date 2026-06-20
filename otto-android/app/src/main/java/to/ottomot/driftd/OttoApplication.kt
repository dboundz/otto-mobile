package to.ottomot.driftd

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.mapbox.common.MapboxOptions
import com.mapbox.maps.Style
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import to.ottomot.driftd.core.analytics.OttoAnalytics
import to.ottomot.driftd.core.notify.OttoNotificationChannels
import to.ottomot.driftd.debug.DebugAndroidAuto

class OttoApplication :
    Application(),
    ImageLoaderFactory {
    /** Long-lived scope for datastore-backed `StateFlow` (matches process lifetime). */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        OttoAnalytics.configure(this)
        OttoNotificationChannels.ensureCreated(this)
        prewarmMapboxConfiguration()
        container = AppContainer(this, applicationScope)
        DebugAndroidAuto.register(this)
        applicationScope.launch {
            container.sessionRepository.authUserIdState.collect { userId ->
                if (userId.isNullOrBlank()) {
                    OttoAnalytics.clearUserID()
                } else {
                    OttoAnalytics.setUserID(userId)
                }
            }
        }
    }

    /**
     * Single process-wide [ImageLoader] for [coil.compose.AsyncImage].
     *
     * Feed images (avatars, event banners, link previews, etc.) reuse a dedicated disk LRU and tune
     * memory retention. [respectCacheHeaders] stays false so CDN / short-lived signed URLs still land
     * in Coil disk when servers omit favorable Cache-Control headers.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .crossfade(true)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(this@OttoApplication)
                    .maxSizePercent(MEMORY_CACHE_PERCENT)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("otto_media_disk_cache"))
                    .maxSizeBytes(MEDIA_DISK_CACHE_BYTES)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()

    private fun prewarmMapboxConfiguration() {
        val token = BuildConfig.MAPBOX_ACCESS_TOKEN.trim()
        Log.d("AndroidPhoneMap", "Mapbox token present: ${token.isNotBlank()}")
        Log.d("AndroidPhoneMap", "Style URI: ${Style.DARK}")
        if (token.isNotBlank() && Style.DARK.isNotBlank()) {
            MapboxOptions.accessToken = token
        }
    }

    private companion object {
        private const val MEMORY_CACHE_PERCENT = 0.22
        private const val MEDIA_DISK_CACHE_BYTES = 192L * 1024 * 1024
    }
}

fun Context.appContainer(): AppContainer =
    (applicationContext as OttoApplication).container
