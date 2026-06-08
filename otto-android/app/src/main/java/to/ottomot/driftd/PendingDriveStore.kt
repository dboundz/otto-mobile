package to.ottomot.driftd

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.time.Instant
import java.util.UUID

data class PendingDrivePathSampleDto(
    val lat: Double,
    val lng: Double,
    val speedMph: Double,
    val capturedAt: String? = null,
)

data class PendingDriveArchiveDto(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: String = Instant.now().toString(),
    val expiresAt: String,
    val retryCount: Int = 0,
    val failurePhase: String,
    val kind: String,
    val title: String,
    val startedAt: String,
    val endedAt: String,
    val distanceMeters: Double,
    val maxSpeedMph: Double,
    val avgSpeedMph: Double,
    val backendDriveId: String? = null,
    val circleId: String? = null,
    val sharedCircleIds: List<String> = emptyList(),
    val routeId: String? = null,
    val routeName: String? = null,
    val pathSamples: List<PendingDrivePathSampleDto> = emptyList(),
)

object PendingDriveStore {
    private const val FILE_NAME = "pending-drive-archives.json"
    private val retentionSeconds = 3L * 24L * 3600L
    private val gson = Gson()

    fun load(context: Context): List<PendingDriveArchiveDto> {
        val file = file(context)
        if (!file.exists()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<PendingDriveArchiveDto>>() {}.type
            val decoded: List<PendingDriveArchiveDto> = gson.fromJson(file.readText(), type) ?: emptyList()
            val now = Instant.now()
            decoded.filter { archive ->
                runCatching { Instant.parse(archive.expiresAt).isAfter(now) }.getOrDefault(false)
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, archives: List<PendingDriveArchiveDto>) {
        val file = file(context)
        file.parentFile?.mkdirs()
        val now = Instant.now()
        val live =
            archives.filter { archive ->
                runCatching { Instant.parse(archive.expiresAt).isAfter(now) }.getOrDefault(false)
            }
        file.writeText(gson.toJson(live))
    }

    fun clear(context: Context) {
        file(context).delete()
    }

    fun makeArchive(
        failurePhase: String,
        kind: String,
        title: String,
        startedAt: Instant,
        endedAt: Instant,
        distanceMeters: Double,
        maxSpeedMph: Double,
        avgSpeedMph: Double,
        backendDriveId: String?,
        circleId: String?,
        sharedCircleIds: List<String>,
        routeId: String? = null,
        routeName: String? = null,
        pathSamples: List<PendingDrivePathSampleDto>,
        retryCount: Int = 0,
    ): PendingDriveArchiveDto? {
        if (distanceMeters <= 0.0 && pathSamples.size < 2) return null
        val now = Instant.now()
        return PendingDriveArchiveDto(
            expiresAt = now.plusSeconds(retentionSeconds).toString(),
            retryCount = retryCount,
            failurePhase = failurePhase,
            kind = kind,
            title = title,
            startedAt = startedAt.toString(),
            endedAt = endedAt.toString(),
            distanceMeters = distanceMeters,
            maxSpeedMph = maxSpeedMph,
            avgSpeedMph = avgSpeedMph,
            backendDriveId = backendDriveId,
            circleId = circleId,
            sharedCircleIds = sharedCircleIds,
            routeId = routeId,
            routeName = routeName,
            pathSamples = pathSamples,
        )
    }

    private fun file(context: Context): File =
        File(context.filesDir, FILE_NAME)
}
