package to.ottomot.driftd

import android.app.Application
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import to.ottomot.driftd.core.auth.AuthRepository
import to.ottomot.driftd.core.data.OttoDataRepository
import to.ottomot.driftd.core.location.ActivityRecognitionPresenceSupport
import to.ottomot.driftd.core.location.ApproximateLocationReader
import to.ottomot.driftd.core.location.DeviceLocationTracker
import to.ottomot.driftd.core.config.OttoEndpoints
import to.ottomot.driftd.core.network.AuthHeaderInterceptor
import to.ottomot.driftd.core.network.ClientTelemetryQueryInterceptor
import to.ottomot.driftd.core.network.UnauthorizedResponseInterceptor
import to.ottomot.driftd.core.network.dto.CircleChatDriveAttachmentDto
import to.ottomot.driftd.core.network.dto.CircleChatEventAttachmentDto
import to.ottomot.driftd.core.network.gson.CircleChatDriveAttachmentDtoDeserializer
import to.ottomot.driftd.core.network.gson.CircleChatEventAttachmentDtoDeserializer
import to.ottomot.driftd.core.network.OttoHttpApi
import to.ottomot.driftd.core.session.SessionRepository
import java.util.concurrent.TimeUnit

class AppContainer internal constructor(
    application: Application,
    applicationScope: CoroutineScope,
) {

    internal val application: Application = application

    internal val approximateLocationReader = ApproximateLocationReader(application)

    internal val deviceLocationTracker = DeviceLocationTracker(application)

    internal val activityRecognitionPresenceSupport =
        ActivityRecognitionPresenceSupport(application)

    internal val sessionRepository =
        SessionRepository(
            datastore = application.ottoPrefs,
            applicationScope = applicationScope,
        )

    internal val okhttp: OkHttpClient

    internal val gson: Gson

    internal val retrofit: Retrofit

    internal val httpApi: OttoHttpApi

    internal val authRepository: AuthRepository

    internal val dataRepository: OttoDataRepository

    init {
        val authInterceptor = AuthHeaderInterceptor(sessionRepository.authTokenState)

        val loggingInterceptor =
            HttpLoggingInterceptor().apply {
                level =
                    if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
            }

        val clientVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        val telemetryInterceptor =
            ClientTelemetryQueryInterceptor(platform = "android", appVersion = clientVersion)

        okhttp =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(telemetryInterceptor)
                .addInterceptor(authInterceptor)
                .apply {
                    if (BuildConfig.DEBUG) {
                        addInterceptor(loggingInterceptor)
                    }
                }
                .addInterceptor(UnauthorizedResponseInterceptor(sessionRepository))
                .build()

        gson =
            GsonBuilder()
                .serializeNulls()
                .registerTypeAdapter(
                    CircleChatEventAttachmentDto::class.java,
                    CircleChatEventAttachmentDtoDeserializer(),
                )
                .registerTypeAdapter(
                    CircleChatDriveAttachmentDto::class.java,
                    CircleChatDriveAttachmentDtoDeserializer(),
                )
                .create()

        retrofit =
            Retrofit.Builder()
                .baseUrl(OttoEndpoints.httpBaseUrl)
                .client(okhttp)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()

        httpApi =
            retrofit.create(OttoHttpApi::class.java)

        authRepository =
            AuthRepository(
                api = httpApi,
                sessionRepository = sessionRepository,
            )

        dataRepository = OttoDataRepository(api = httpApi, gson = gson)
    }
}
