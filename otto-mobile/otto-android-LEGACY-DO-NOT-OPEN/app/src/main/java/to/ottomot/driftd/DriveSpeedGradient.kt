package to.ottomot.driftd

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.style.expressions.generated.Expression
import to.ottomot.driftd.core.network.dto.DrivePathPointDto
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class DrivePathSample(
    val lat: Double,
    val lng: Double,
    val speedMph: Double,
    val capturedAtIso: String? = null,
) {
    fun toPoint(): Point = Point.fromLngLat(lng, lat)
}

internal object DriveSpeedGradient {
    private const val MAX_SPEED_MPH = 200.0
    private const val MAX_RENDER_VERTICES = 720
    private const val MAX_GRADIENT_STOPS = 64

    private val speedStops =
        listOf(
            0.0 to AndroidColor.rgb(0, 209, 255),
            40.0 to AndroidColor.rgb(0, 136, 255),
            80.0 to AndroidColor.rgb(91, 91, 255),
            120.0 to AndroidColor.rgb(176, 38, 255),
            160.0 to AndroidColor.rgb(255, 106, 0),
            200.0 to AndroidColor.rgb(255, 213, 0),
        )

    data class RenderVertex(
        val lat: Double,
        val lng: Double,
        val speedMph: Double,
        val lineProgress: Double,
    )

    data class LineSegment(
        val id: String,
        val coordinates: List<Point>,
        val color: Int,
    )

    fun speedColorHex(speedMph: Double): String =
        String.format("%06X", getSpeedColor(speedMph) and 0xFFFFFF)

    fun buildGradientSegments(
        samples: List<DrivePathSample>,
        idPrefix: String = "speed-segment",
        maxCount: Int = 500,
    ): List<LineSegment> {
        val vertices = buildRenderVertices(samples)
        if (vertices.size < 2) return emptyList()

        val segments =
            (0 until vertices.size - 1).map { index ->
                val start = vertices[index]
                val end = vertices[index + 1]
                val startColor = getSpeedColor(start.speedMph)
                val endColor = getSpeedColor(end.speedMph)
                LineSegment(
                    id = "$idPrefix-$index",
                    coordinates =
                        listOf(
                            Point.fromLngLat(start.lng, start.lat),
                            Point.fromLngLat(end.lng, end.lat),
                        ),
                    color = interpolateColor(startColor, endColor, 0.5),
                )
            }
        return thinSegmentsPreservingColor(segments, maxCount)
    }

    fun from(dto: DrivePathPointDto): DrivePathSample? {
        if (!dto.lat.isFinite() || !dto.lng.isFinite()) return null
        return DrivePathSample(
            lat = dto.lat,
            lng = dto.lng,
            speedMph = dto.speedMph,
            capturedAtIso = dto.capturedAt,
        )
    }

    fun clampSpeed(speed: Double): Double {
        if (!speed.isFinite()) return 0.0
        return min(MAX_SPEED_MPH, max(0.0, speed))
    }

    fun getSpeedColor(speed: Double): Int {
        val mph = clampSpeed(speed)
        val first = speedStops.first()
        if (mph <= first.first) return first.second
        for (index in 1 until speedStops.size) {
            val upper = speedStops[index]
            if (mph <= upper.first) {
                val lower = speedStops[index - 1]
                val span = max(upper.first - lower.first, 0.0001)
                val t = (mph - lower.first) / span
                return interpolateColor(lower.second, upper.second, t)
            }
        }
        return speedStops.last().second
    }

    fun trailGradientExpression(vertices: List<RenderVertex>): Expression? =
        lineGradientExpression(vertices, ::getSpeedColor)

    fun hasUsableSpeedPathData(samples: List<DrivePathSample>): Boolean {
        val coordinates = pathCoordinates(samples)
        if (coordinates.size < 2) return false
        var totalDistanceMeters = 0.0
        for (index in 1 until coordinates.size) {
            totalDistanceMeters += coordinateDistanceMeters(coordinates[index - 1], coordinates[index])
        }
        return totalDistanceMeters >= 20.0
    }

    fun pathCoordinates(samples: List<DrivePathSample>): List<Point> =
        enrichedSamples(samples).map { it.toPoint() }

    fun polylineDistanceMeters(samples: List<DrivePathSample>): Double {
        val coordinates = pathCoordinates(samples)
        if (coordinates.size < 2) return 0.0
        var total = 0.0
        for (index in 1 until coordinates.size) {
            total += coordinateDistanceMeters(coordinates[index - 1], coordinates[index])
        }
        return total
    }

    fun buildRenderVertices(samples: List<DrivePathSample>): List<RenderVertex> {
        val prepared = samplesForRendering(samples)
        if (prepared.size < 2) return emptyList()

        val densified = ArrayList<Pair<Point, Double>>()
        densified.add(prepared[0].toPoint() to clampSpeed(prepared[0].speedMph))

        for (index in 0 until prepared.size - 1) {
            val start = prepared[index]
            val end = prepared[index + 1]
            val distanceMeters = coordinateDistanceMeters(start, end)
            val startSpeed = clampSpeed(start.speedMph)
            val endSpeed = clampSpeed(end.speedMph)
            val speedDelta = abs(endSpeed - startSpeed)
            val steps = subdivisionCount(distanceMeters, speedDelta, startSpeed, endSpeed)

            if (steps <= 1) {
                densified.add(end.toPoint() to endSpeed)
                continue
            }

            for (step in 1..steps) {
                val t = step.toDouble() / steps.toDouble()
                densified.add(interpolatePoint(start, end, t) to interpolateSpeed(startSpeed, endSpeed, t))
            }
        }

        val thinned = thinVerticesPreservingSpeed(densified, MAX_RENDER_VERTICES)
        return verticesWithLineProgress(thinned)
    }

    /** Matches iOS `legendLinearGradient` (0–200 mph color scale for the trail legend). */
    fun legendBrush(): Brush {
        val sampleCount = 40
        val colors =
            (0 until sampleCount).map { index ->
                val mph = MAX_SPEED_MPH * index / (sampleCount - 1).coerceAtLeast(1)
                Color(getSpeedColor(mph))
            }
        return Brush.horizontalGradient(colors)
    }

    private fun lineGradientExpression(
        vertices: List<RenderVertex>,
        colorForSpeed: (Double) -> Int,
    ): Expression? {
        if (vertices.size < 2) return null
        val stops = gradientStops(vertices)
        if (stops.size < 2) return null

        val arguments = ArrayList<Expression>()
        arguments.add(Expression.linear())
        arguments.add(Expression.lineProgress())
        for (stop in stops) {
            arguments.add(Expression.literal(stop.lineProgress))
            arguments.add(Expression.color(colorForSpeed(stop.speedMph)))
        }
        return Expression.interpolate(*arguments.toTypedArray())
    }

    private fun gradientStops(vertices: List<RenderVertex>): List<RenderVertex> {
        if (vertices.isEmpty()) return emptyList()
        val stops = ArrayList<RenderVertex>()
        stops.add(vertices.first())
        for (vertex in vertices.drop(1)) {
            val last = stops.last()
            val progressDelta = abs(vertex.lineProgress - last.lineProgress)
            val speedDelta = abs(vertex.speedMph - last.speedMph)
            if (progressDelta >= 0.002 || speedDelta >= 1.2 || stops.size == 1) {
                stops.add(vertex)
            }
        }
        if (stops.last().lineProgress != vertices.last().lineProgress) {
            stops.add(vertices.last())
        }
        return if (stops.size > MAX_GRADIENT_STOPS) thinEvenly(stops, MAX_GRADIENT_STOPS) else stops
    }

    private fun thinEvenly(stops: List<RenderVertex>, maxCount: Int): List<RenderVertex> {
        if (stops.size <= maxCount || maxCount < 2) return stops
        val result = ArrayList<RenderVertex>(maxCount)
        val stride = (stops.size - 1).toDouble() / (maxCount - 1).toDouble()
        for (index in 0 until maxCount) {
            val sourceIndex = min(stops.size - 1, (index * stride).roundToInt())
            result.add(stops[sourceIndex])
        }
        if (result.first().lineProgress != stops.first().lineProgress) {
            result[0] = stops.first()
        }
        if (result.last().lineProgress != stops.last().lineProgress) {
            result[result.size - 1] = stops.last()
        }
        return result
    }

    private fun samplesForRendering(samples: List<DrivePathSample>): List<DrivePathSample> {
        val enriched = enrichedSamples(samples)
        if (enriched.size < 2) return enriched
        val speeds = enriched.map { it.speedMph }
        val minSpeed = speeds.minOrNull() ?: return enriched
        val maxSpeed = speeds.maxOrNull() ?: return enriched
        val span = maxSpeed - minSpeed
        if (span < 2) return enriched
        if (maxSpeed > 95) return enriched
        val displayTop = min(110.0, maxOf(52.0, span * 6, maxSpeed + 12))
        return enriched.map { sample ->
            val t = (sample.speedMph - minSpeed) / span
            sample.copy(speedMph = clampSpeed(t * displayTop))
        }
    }

    private fun enrichedSamples(samples: List<DrivePathSample>): List<DrivePathSample> {
        val deduped = deduplicatedSamples(samples)
        if (deduped.isEmpty()) return emptyList()
        val enriched = ArrayList<DrivePathSample>(deduped.size)
        for (index in deduped.indices) {
            val sample = deduped[index]
            var effective = clampSpeed(sample.speedMph)
            if (index > 0) {
                val previous = deduped[index - 1]
                val distanceMeters = coordinateDistanceMeters(previous, sample)
                val previousAt = parseInstantMillis(previous.capturedAtIso)
                val currentAt = parseInstantMillis(sample.capturedAtIso)
                if (previousAt != null && currentAt != null) {
                    val elapsedSeconds = (currentAt - previousAt) / 1000.0
                    if (elapsedSeconds in 0.35..180.0) {
                        val inferredMph = clampSpeed((distanceMeters / elapsedSeconds) * 2.23694)
                        effective =
                            when {
                                distanceMeters < 5 && inferredMph < 3 -> 0.0
                                effective < 3 || effective < inferredMph * 0.5 -> inferredMph
                                abs(inferredMph - effective) >= 4 -> inferredMph
                                else -> max(effective, inferredMph)
                            }
                    }
                } else if (distanceMeters < 4) {
                    effective = 0.0
                }
            }
            enriched.add(sample.copy(speedMph = clampSpeed(effective)))
        }
        return enriched
    }

    private fun deduplicatedSamples(samples: List<DrivePathSample>): List<DrivePathSample> {
        val kept = ArrayList<DrivePathSample>()
        for (sample in samples) {
            val last = kept.lastOrNull()
            if (last != null && coordinateDistanceMeters(last, sample) < 1.0) continue
            kept.add(sample)
        }
        return kept
    }

    private fun verticesWithLineProgress(
        vertices: List<Pair<Point, Double>>,
    ): List<RenderVertex> {
        if (vertices.isEmpty()) return emptyList()
        if (vertices.size == 1) {
            val only = vertices.first()
            return listOf(RenderVertex(only.first.latitude(), only.first.longitude(), only.second, 0.0))
        }
        val cumulative = ArrayList<Double>(vertices.size)
        cumulative.add(0.0)
        for (index in 1 until vertices.size) {
            val segment =
                haversineMeters(
                    vertices[index - 1].first.latitude(),
                    vertices[index - 1].first.longitude(),
                    vertices[index].first.latitude(),
                    vertices[index].first.longitude(),
                )
            cumulative.add(cumulative[index - 1] + segment)
        }
        val total = cumulative.lastOrNull() ?: 0.0
        val scale = if (total > 0) 1.0 / total else 0.0
        return vertices.mapIndexed { index, (point, speed) ->
            RenderVertex(
                lat = point.latitude(),
                lng = point.longitude(),
                speedMph = speed,
                lineProgress = min(1.0, max(0.0, cumulative[index] * scale)),
            )
        }
    }

    private fun thinVerticesPreservingSpeed(
        vertices: List<Pair<Point, Double>>,
        maxCount: Int,
    ): List<Pair<Point, Double>> {
        if (vertices.size <= maxCount || maxCount < 2) return vertices
        val kept = ArrayList<Pair<Point, Double>>()
        kept.add(vertices.first())
        for (index in 1 until vertices.size - 1) {
            val candidate = vertices[index]
            val last = kept.last()
            if (abs(candidate.second - last.second) >= 2) {
                kept.add(candidate)
            }
        }
        kept.add(vertices.last())
        if (kept.size <= maxCount) return kept
        val thinned = ArrayList<Pair<Point, Double>>(maxCount)
        val stride = (kept.size - 1).toDouble() / (maxCount - 1).toDouble()
        for (index in 0 until maxCount) {
            val sourceIndex = min(kept.size - 1, (index * stride).roundToInt())
            thinned.add(kept[sourceIndex])
        }
        if (thinned.first() != kept.first()) thinned[0] = kept.first()
        if (thinned.last() != kept.last()) thinned[thinned.size - 1] = kept.last()
        return thinned
    }

    private fun subdivisionCount(
        distanceMeters: Double,
        speedDelta: Double,
        startSpeed: Double,
        endSpeed: Double,
    ): Int {
        val distanceSteps = kotlin.math.ceil(distanceMeters / 18.0).toInt()
        val speedSteps = kotlin.math.ceil(speedDelta / 3.0).toInt()
        var steps = min(32, max(1, max(distanceSteps, speedSteps)))
        val nearStop = min(startSpeed, endSpeed) < 6
        val moving = max(startSpeed, endSpeed) > 12
        if (nearStop && moving) {
            steps = max(steps, kotlin.math.ceil(speedDelta / 3.0).toInt())
        }
        return min(28, steps)
    }

    private fun interpolateSpeed(startSpeed: Double, endSpeed: Double, t: Double): Double {
        val clampedT = min(1.0, max(0.0, t))
        val start = clampSpeed(startSpeed)
        val end = clampSpeed(endSpeed)
        return start + (end - start) * clampedT
    }

    private fun interpolatePoint(start: DrivePathSample, end: DrivePathSample, t: Double): Point {
        val clampedT = min(1.0, max(0.0, t))
        return Point.fromLngLat(
            start.lng + (end.lng - start.lng) * clampedT,
            start.lat + (end.lat - start.lat) * clampedT,
        )
    }

    private fun coordinateDistanceMeters(a: DrivePathSample, b: DrivePathSample): Double =
        haversineMeters(a.lat, a.lng, b.lat, b.lng)

    private fun coordinateDistanceMeters(a: Point, b: Point): Double =
        haversineMeters(a.latitude(), a.longitude(), b.latitude(), b.longitude())

    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a =
            kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return r * c
    }

    private fun interpolateColor(colorA: Int, colorB: Int, t: Double): Int {
        val clampedT = min(1f, max(0f, t.toFloat()))
        val a1 = AndroidColor.alpha(colorA) / 255f
        val r1 = AndroidColor.red(colorA) / 255f
        val g1 = AndroidColor.green(colorA) / 255f
        val b1 = AndroidColor.blue(colorA) / 255f
        val r2 = AndroidColor.red(colorB) / 255f
        val g2 = AndroidColor.green(colorB) / 255f
        val b2 = AndroidColor.blue(colorB) / 255f
        return AndroidColor.argb(
            (a1 * 255).roundToInt(),
            ((r1 + (r2 - r1) * clampedT) * 255).roundToInt(),
            ((g1 + (g2 - g1) * clampedT) * 255).roundToInt(),
            ((b1 + (b2 - b1) * clampedT) * 255).roundToInt(),
        )
    }

    private fun colorsAreSimilar(lhs: Int, rhs: Int): Boolean {
        val delta =
            abs(AndroidColor.red(lhs) - AndroidColor.red(rhs)) +
                abs(AndroidColor.green(lhs) - AndroidColor.green(rhs)) +
                abs(AndroidColor.blue(lhs) - AndroidColor.blue(rhs))
        return delta < 20
    }

    private fun thinSegmentsPreservingColor(
        segments: List<LineSegment>,
        maxCount: Int,
    ): List<LineSegment> {
        if (segments.size <= maxCount || maxCount < 2) return segments

        val kept = ArrayList<LineSegment>()
        kept.add(segments.first())
        for (index in 1 until segments.size - 1) {
            val candidate = segments[index]
            val lastColor = kept.last().color
            if (!colorsAreSimilar(lastColor, candidate.color)) {
                kept.add(candidate)
            }
        }
        kept.add(segments.last())

        if (kept.size <= maxCount) return kept

        val step = ceil(kept.size.toDouble() / maxCount.toDouble()).toInt().coerceAtLeast(1)
        val thinned = ArrayList<LineSegment>()
        var index = 0
        while (index < kept.size) {
            thinned.add(kept[index])
            index += step
        }
        if (thinned.last().id != kept.last().id) {
            thinned.add(kept.last())
        }
        return thinned
    }

    private fun parseInstantMillis(iso: String?): Long? =
        runCatching { java.time.Instant.parse(iso?.trim().orEmpty()).toEpochMilli() }.getOrNull()
}
