package to.ottomot.driftd.routebuilder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.ui.graphics.toArgb
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import to.ottomot.driftd.RouteMapMarkerAsset

/** Bitmap-backed [PointAnnotationOptions] for Route Builder (Mapbox annotation plugin). */
internal class RouteBuilderMapMarkerBitmaps(
    private val appContext: Context,
    private val density: Float,
) {
    private val bitmaps = mutableMapOf<String, Bitmap>()

    fun iconBitmap(marker: RouteBuilderMapMarkerSnapshot): Bitmap? {
        val spec = iconSpec(marker) ?: return null
        return bitmaps.getOrPut(spec.cacheKey) {
            when (spec) {
                is IconSpec.Drawable -> loadBitmap(spec.drawableRes, spec.widthPx, spec.heightPx)
                is IconSpec.Dot -> Bitmap.createBitmap(spec.widthPx, spec.heightPx, Bitmap.Config.ARGB_8888).withDot(spec)
            }
        }
    }

    fun toPointAnnotationOptions(marker: RouteBuilderMapMarkerSnapshot): PointAnnotationOptions? {
        val spec = iconSpec(marker) ?: return null
        val bitmap = iconBitmap(marker) ?: return null
        val anchor =
            if (routeBuilderMarkerUsesCenterAnchor(marker.presentation, marker.markerType)) {
                IconAnchor.CENTER
            } else {
                IconAnchor.BOTTOM
            }
        return PointAnnotationOptions()
            .withPoint(Point.fromLngLat(marker.lng, marker.lat))
            .withIconImage(bitmap)
            .withIconAnchor(anchor)
            .withIconSize(spec.iconScale)
            .withIconOpacity(if (marker.isAutoShape) 0.72 else 1.0)
            .withSymbolSortKey(routeBuilderMarkerSortKey(marker))
    }

    private fun loadBitmap(
        @DrawableRes drawableRes: Int,
        widthPx: Int,
        heightPx: Int,
    ): Bitmap {
        val drawable =
            AppCompatResources.getDrawable(appContext, drawableRes)
                ?: error("Missing drawable $drawableRes")
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, widthPx, heightPx)
        drawable.draw(canvas)
        return bitmap
    }

    private fun iconSpec(marker: RouteBuilderMapMarkerSnapshot): IconSpec? {
        val pinHeightPx = (84f * density).toInt().coerceAtLeast(48)
        val pinWidthPx = (56f * density).toInt().coerceAtLeast(40)
        val badgePx = (48f * density).toInt().coerceAtLeast(32)
        if (marker.presentation == RouteBuilderMapMarkerPresentation.DOT) {
            val framePx = (44f * density).toInt().coerceAtLeast(32)
            val radiusPx = 6f * density
            val strokePx = 1.5f * density
            return IconSpec.Dot(
                cacheKey = "dot-${marker.markerType}-${marker.dotColor.toArgb()}-${marker.isAutoShape}",
                widthPx = framePx,
                heightPx = framePx,
                color = marker.dotColor.toArgb(),
                radiusPx = radiusPx,
                strokePx = strokePx,
                iconScale = 1.0,
            )
        }
        val pinScale = marker.pinScale.coerceAtLeast(0.1f)
        return when (marker.markerType) {
            "start" ->
                IconSpec.Drawable(
                    cacheKey = "start",
                    drawableRes = RouteMapMarkerAsset.drawableRes("start"),
                    widthPx = pinWidthPx,
                    heightPx = pinHeightPx,
                    iconScale = 1.0,
                )
            "finish" ->
                IconSpec.Drawable(
                    cacheKey = "finish",
                    drawableRes = RouteMapMarkerAsset.drawableRes("finish"),
                    widthPx = pinWidthPx,
                    heightPx = pinHeightPx,
                    iconScale = 1.0,
                )
            "waypoint" ->
                IconSpec.Drawable(
                    cacheKey = "waypoint-${RouteBuilderMarkerLod.pinScaleBucketForScale(pinScale)}",
                    drawableRes = RouteMapMarkerAsset.drawableRes("waypoint"),
                    widthPx = pinWidthPx,
                    heightPx = pinHeightPx,
                    iconScale = pinScale.toDouble(),
                )
            "stop" ->
                IconSpec.Drawable(
                    cacheKey = "stop-${RouteBuilderMarkerLod.pinScaleBucketForScale(pinScale)}",
                    drawableRes = RouteMapMarkerAsset.drawableRes("stop"),
                    widthPx = pinWidthPx,
                    heightPx = pinHeightPx,
                    iconScale = pinScale.toDouble(),
                )
            "path" -> {
                IconSpec.Drawable(
                    cacheKey = "path-${RouteBuilderMarkerLod.pinScaleBucketForScale(pinScale)}-${marker.isAutoShape}",
                    drawableRes = RouteMapMarkerAsset.drawableRes("path"),
                    widthPx = badgePx,
                    heightPx = badgePx,
                    iconScale = pinScale.toDouble(),
                )
            }
            else -> null
        }
    }

    private fun Bitmap.withDot(spec: IconSpec.Dot): Bitmap {
        val canvas = Canvas(this)
        val center = width / 2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = spec.color
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = android.graphics.Color.WHITE
            strokeWidth = spec.strokePx
        }
        canvas.drawCircle(center, center, spec.radiusPx, fill)
        canvas.drawCircle(center, center, spec.radiusPx, stroke)
        return this
    }

    private sealed class IconSpec {
        abstract val cacheKey: String
        abstract val widthPx: Int
        abstract val heightPx: Int
        abstract val iconScale: Double

        data class Drawable(
            override val cacheKey: String,
            @param:DrawableRes val drawableRes: Int,
            override val widthPx: Int,
            override val heightPx: Int,
            override val iconScale: Double,
        ) : IconSpec()

        data class Dot(
            override val cacheKey: String,
            override val widthPx: Int,
            override val heightPx: Int,
            val color: Int,
            val radiusPx: Float,
            val strokePx: Float,
            override val iconScale: Double,
        ) : IconSpec()
    }
}

internal fun routeBuilderMarkerUsesCenterAnchor(
    presentation: RouteBuilderMapMarkerPresentation,
    markerType: String?,
): Boolean =
    when (presentation) {
        RouteBuilderMapMarkerPresentation.DOT -> true
        RouteBuilderMapMarkerPresentation.ENDPOINT_PIN,
        RouteBuilderMapMarkerPresentation.PIN,
        -> RouteMapMarkerAsset.usesCenteredMarker(markerType)
    }

private fun routeBuilderMarkerSortKey(marker: RouteBuilderMapMarkerSnapshot): Double =
    when (marker.markerType) {
        "start" -> 30.0
        "finish" -> 29.0
        "stop" -> 20.0
        "waypoint" -> 15.0
        "path" -> if (marker.isAutoShape) 5.0 else 10.0
        else -> 0.0
    }
