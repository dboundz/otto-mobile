package to.ottomot.driftd.core.config

import to.ottomot.driftd.BuildConfig

/** Build-time HTTP and WebSocket roots (debug vs release), matching iOS `APIConfig`. */
object OttoEndpoints {
    val httpBaseUrl: String get() = BuildConfig.API_BASE_URL

    val webSocketUrl: String get() = BuildConfig.WEBSOCKET_URL
}
