package to.ottomot.driftd.map

import java.util.concurrent.TimeUnit

enum class TravelSurface {
    Land,
    Water,
}

object MapTravelSurfaceSampler {
    /** Boat-on-water map chips. Off: Tilequery polled every 2s per moving sharer per device (costly; cosmetic). */
    const val WATER_SURFACE_DETECTION_ENABLED = false

    const val MIN_SPEED_MPH_FOR_BOAT = 2.0
    private val CONFIRMATION_MS = TimeUnit.SECONDS.toMillis(4)
    private val SAMPLE_THROTTLE_MS = TimeUnit.SECONDS.toMillis(2)

    fun instantaneousSurface(
        speedMph: Double,
        onWater: Boolean,
        onRoad: Boolean,
    ): TravelSurface {
        if (speedMph < MIN_SPEED_MPH_FOR_BOAT) return TravelSurface.Land
        if (!onWater || onRoad) return TravelSurface.Land
        return TravelSurface.Water
    }

    fun isWaterLayer(layer: String): Boolean {
        val tokens = listOf("water", "waterway", "lake", "ocean", "river")
        return tokens.any { layer.contains(it) }
    }

    fun isRoadLayer(layer: String): Boolean {
        val tokens = listOf("road", "bridge", "highway", "street", "motorway", "trunk", "transportation")
        return tokens.any { layer.contains(it) }
    }

    fun classifyFeatureLayers(
        sourceLayer: String?,
        layerId: String?,
    ): Pair<Boolean, Boolean> {
        val source = sourceLayer.orEmpty().lowercase()
        val layer = layerId.orEmpty().lowercase()
        val onWater = isWaterLayer(source) || isWaterLayer(layer)
        val onRoad = isRoadLayer(source) || isRoadLayer(layer)
        return onWater to onRoad
    }

    fun sampleThrottleMs(): Long = SAMPLE_THROTTLE_MS

    fun confirmationMs(): Long = CONFIRMATION_MS
}

class TravelSurfaceHysteresis {
    var displayed: TravelSurface = TravelSurface.Land
        private set

    private var pending: TravelSurface? = null
    private var pendingSinceMs: Long? = null

    fun reset() {
        displayed = TravelSurface.Land
        pending = null
        pendingSinceMs = null
    }

    fun apply(
        instantaneous: TravelSurface,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (instantaneous == displayed) {
            pending = null
            pendingSinceMs = null
            return
        }
        if (pending == instantaneous) {
            val since = pendingSinceMs ?: return
            if (nowMs - since >= MapTravelSurfaceSampler.confirmationMs()) {
                displayed = instantaneous
                pending = null
                pendingSinceMs = null
            }
        } else {
            pending = instantaneous
            pendingSinceMs = nowMs
        }
    }
}

class TravelSurfaceTracker {
    private val controllers = linkedMapOf<String, TravelSurfaceHysteresis>()
    private val lastSampleAtMs = linkedMapOf<String, Long>()
    private val surfacesByUserId = linkedMapOf<String, TravelSurface>()

    fun surfaceFor(userId: String): TravelSurface = surfacesByUserId[userId] ?: TravelSurface.Land

    fun snapshot(): Map<String, TravelSurface> = surfacesByUserId.toMap()

    fun removeUsersNotIn(ids: Set<String>) {
        controllers.keys.retainAll(ids)
        surfacesByUserId.keys.retainAll(ids)
        lastSampleAtMs.keys.retainAll(ids)
    }

    fun ingest(
        userId: String,
        instantaneous: TravelSurface,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val controller = controllers.getOrPut(userId) { TravelSurfaceHysteresis() }
        controller.apply(instantaneous, nowMs)
        surfacesByUserId[userId] = controller.displayed
    }

    fun shouldSample(
        userId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val last = lastSampleAtMs[userId] ?: return true
        return nowMs - last >= MapTravelSurfaceSampler.sampleThrottleMs()
    }

    fun markSampled(
        userId: String,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        lastSampleAtMs[userId] = nowMs
    }
}
