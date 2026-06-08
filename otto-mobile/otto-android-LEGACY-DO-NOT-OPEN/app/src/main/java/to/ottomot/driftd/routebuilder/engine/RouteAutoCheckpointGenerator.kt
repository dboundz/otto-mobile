package to.ottomot.driftd.routebuilder.engine

/**
 * Auto checkpoint spacing and density tiers (ported from iOS [RouteAutoCheckpointGenerator]).
 */
enum class CheckpointDensityTier {
    FEWER,
    RECOMMENDED,
    MORE,
    MAXIMUM,
    ;

    val titleStringResKey: String
        get() =
            when (this) {
                FEWER -> "route_builder_density_fewer"
                RECOMMENDED -> "route_builder_density_recommended"
                MORE -> "route_builder_density_more"
                MAXIMUM -> "route_builder_density_maximum"
            }
}

object RouteAutoCheckpointGenerator {
    data class IntervalOption(
        val miles: Double,
        val spacingMeters: Double,
        val checkpointCount: Int,
    ) {
        val intervalLabel: String
            get() = intervalLabel(miles)

        val summaryText: String
            get() {
                val plural = if (checkpointCount == 1) "" else "s"
                return "Every $intervalLabel · ~$checkpointCount checkpoint$plural"
            }
    }

    data class DensityOption(
        val tier: CheckpointDensityTier,
        val spacingMeters: Double,
        val checkpointCount: Int,
        val averageSpacingMiles: Double,
    )

    object Options {
        const val METERS_PER_MILE = 1609.344
        const val HALF_MILE_METERS = METERS_PER_MILE * 0.5
        const val FINISH_BUFFER_METERS = 100.0
        val STANDARD_INTERVAL_MILES: List<Double> = listOf(0.5, 1.0, 2.0, 5.0, 10.0, 100.0)
        const val MAX_RECOMMENDED_CHECKPOINTS = 12
        const val HIGH_COMPLEXITY_TURNS_PER_MILE = 4.0
    }

    fun spacingMeters(forMiles: Double): Double = forMiles * Options.METERS_PER_MILE

    fun intervalLabel(miles: Double): String =
        when {
            miles == 0.5 -> "½ mile"
            miles == miles.toLong().toDouble() -> {
                val whole = miles.toInt()
                if (whole == 1) "1 mile" else "$whole miles"
            }
            else -> String.format("%.1f miles", miles)
        }

    fun canOfferAutoCheckpoints(roadCoordinates: List<RouteLatLng>): Boolean =
        viableIntervals(roadCoordinates).isNotEmpty()

    fun viableIntervals(roadCoordinates: List<RouteLatLng>): List<IntervalOption> =
        Options.STANDARD_INTERVAL_MILES.mapNotNull { miles ->
            val spacingMeters = spacingMeters(miles)
            val generated = generate(roadCoordinates, spacingMeters)
            if (generated.isEmpty()) return@mapNotNull null
            IntervalOption(
                miles = miles,
                spacingMeters = spacingMeters,
                checkpointCount = generated.size,
            )
        }

    fun routeLengthMiles(roadCoordinates: List<RouteLatLng>): Double {
        if (roadCoordinates.size < 2) return 0.0
        return RoutePolylineGeometry.polylineTotalLength(roadCoordinates) / Options.METERS_PER_MILE
    }

    fun routeComplexityScore(roadCoordinates: List<RouteLatLng>, turnCount: Int): Double {
        val miles = maxOf(routeLengthMiles(roadCoordinates), 0.1)
        val turnsPerMile = turnCount.toDouble() / miles
        return minOf(1.0, turnsPerMile / Options.HIGH_COMPLEXITY_TURNS_PER_MILE)
    }

    fun targetCheckpointCount(
        routeMiles: Double,
        turnCount: Int = 0,
        roadCoordinates: List<RouteLatLng> = emptyList(),
    ): Int {
        val base =
            when {
                routeMiles < 1.5 -> 2
                routeMiles < 4 -> 3
                routeMiles < 10 -> 5
                routeMiles < 25 -> 7
                routeMiles < 50 -> 9
                else -> 11
            }
        val complexity =
            if (roadCoordinates.isEmpty()) {
                minOf(1.0, turnCount.toDouble() / maxOf(routeMiles, 0.1) / Options.HIGH_COMPLEXITY_TURNS_PER_MILE)
            } else {
                routeComplexityScore(roadCoordinates, turnCount)
            }
        val nudge = if (complexity >= 0.75) 1 else 0
        return minOf(base + nudge, Options.MAX_RECOMMENDED_CHECKPOINTS)
    }

    fun recommendedDefaultInterval(
        roadCoordinates: List<RouteLatLng>,
        turnCount: Int = 0,
    ): IntervalOption? {
        val intervals = viableIntervals(roadCoordinates)
        if (intervals.isEmpty()) return null

        val target =
            targetCheckpointCount(
                routeMiles = routeLengthMiles(roadCoordinates),
                turnCount = turnCount,
                roadCoordinates = roadCoordinates,
            )
        val candidates = intervals.filter { it.checkpointCount <= Options.MAX_RECOMMENDED_CHECKPOINTS }
        val pool = candidates.ifEmpty { intervals }

        return pool.minWithOrNull(
            compareBy<IntervalOption> { kotlin.math.abs(it.checkpointCount - target) }
                .thenByDescending { it.miles },
        )
    }

    fun densityOptions(
        roadCoordinates: List<RouteLatLng>,
        turnCount: Int = 0,
    ): List<DensityOption> {
        val intervals = viableIntervals(roadCoordinates)
        if (intervals.isEmpty()) return emptyList()

        val routeMiles = routeLengthMiles(roadCoordinates)
        val recommended = recommendedDefaultInterval(roadCoordinates, turnCount)
        val recommendedIndex =
            recommended?.let { rec ->
                intervals.indexOfFirst { it.spacingMeters == rec.spacingMeters }
            }?.takeIf { it >= 0 } ?: 0

        fun densityOption(tier: CheckpointDensityTier, interval: IntervalOption): DensityOption {
            val avg =
                if (interval.checkpointCount > 0) {
                    routeMiles / (interval.checkpointCount + 1)
                } else {
                    routeMiles
                }
            return DensityOption(
                tier = tier,
                spacingMeters = interval.spacingMeters,
                checkpointCount = interval.checkpointCount,
                averageSpacingMiles = avg,
            )
        }

        val fewerIndex =
            intervals.indices
                .filter { intervals[it].checkpointCount <= (recommended?.checkpointCount ?: Int.MAX_VALUE) }
                .maxByOrNull { intervals[it].miles }
                ?: recommendedIndex

        val moreIndex =
            intervals.indices
                .filter { it < recommendedIndex }
                .maxOrNull()
                ?: maxOf(0, recommendedIndex - 1)

        val maximumInterval =
            intervals
                .filter { it.checkpointCount <= Options.MAX_RECOMMENDED_CHECKPOINTS }
                .minByOrNull { it.miles }
                ?: intervals[0]
        val maximumIndex =
            intervals.indexOfFirst { it.spacingMeters == maximumInterval.spacingMeters }
                .takeIf { it >= 0 } ?: 0

        val tiers =
            listOf(
                CheckpointDensityTier.FEWER to fewerIndex,
                CheckpointDensityTier.RECOMMENDED to recommendedIndex,
                CheckpointDensityTier.MORE to moreIndex,
                CheckpointDensityTier.MAXIMUM to maximumIndex,
            )

        val seen = mutableSetOf<Double>()
        val options = mutableListOf<DensityOption>()
        for ((tier, index) in tiers) {
            val interval = intervals[index]
            if (!seen.add(interval.spacingMeters)) continue
            options.add(densityOption(tier, interval))
        }

        if (options.none { it.tier == CheckpointDensityTier.RECOMMENDED } && recommended != null) {
            options.add(densityOption(CheckpointDensityTier.RECOMMENDED, recommended))
        }

        return options.sortedBy { tierOrder(it.tier) }
    }

    private fun tierOrder(tier: CheckpointDensityTier): Int =
        when (tier) {
            CheckpointDensityTier.FEWER -> 0
            CheckpointDensityTier.RECOMMENDED -> 1
            CheckpointDensityTier.MORE -> 2
            CheckpointDensityTier.MAXIMUM -> 3
        }

    fun densityOption(
        tier: CheckpointDensityTier,
        roadCoordinates: List<RouteLatLng>,
        turnCount: Int = 0,
    ): DensityOption? =
        densityOptions(roadCoordinates, turnCount).firstOrNull { it.tier == tier }

    fun adjacentDensityOption(
        fromTier: CheckpointDensityTier,
        roadCoordinates: List<RouteLatLng>,
        turnCount: Int = 0,
        direction: Int,
    ): DensityOption? {
        val options = densityOptions(roadCoordinates, turnCount)
        if (options.size <= 1) return null

        val currentIndex =
            densityOption(fromTier, roadCoordinates, turnCount)
                ?.let { current ->
                    options.indexOfFirst { it.spacingMeters == current.spacingMeters }
                }
                ?.takeIf { it >= 0 }
                ?: options.indexOfFirst { it.tier == CheckpointDensityTier.RECOMMENDED }
                    .takeIf { it >= 0 }
                ?: 0

        val nextIndex = currentIndex + direction
        if (nextIndex !in options.indices) return null
        val next = options[nextIndex]
        if (next.spacingMeters == options[currentIndex].spacingMeters) return null
        return next
    }

    fun adjacentTier(fromTier: CheckpointDensityTier, direction: Int): CheckpointDensityTier? {
        val all = CheckpointDensityTier.entries
        val index = all.indexOf(fromTier)
        if (index < 0) return null
        val next = index + direction
        if (next !in all.indices) return null
        return all[next]
    }

    fun generate(
        roadCoordinates: List<RouteLatLng>,
        spacingMeters: Double = Options.HALF_MILE_METERS,
    ): List<RouteLatLng> {
        if (roadCoordinates.size < 2) return emptyList()

        val totalLength = RoutePolylineGeometry.polylineTotalLength(roadCoordinates)
        val maxArcLength = maxOf(0.0, totalLength - Options.FINISH_BUFFER_METERS)
        if (maxArcLength <= spacingMeters * 0.5) return emptyList()

        val coordinates = mutableListOf<RouteLatLng>()
        var distance = spacingMeters
        while (distance < maxArcLength) {
            RoutePolylineGeometry.coordinateAtArcLength(distance, roadCoordinates)?.let {
                coordinates.add(it)
            }
            distance += spacingMeters
        }
        return coordinates
    }
}
