package to.ottomot.driftd.core.network.gson

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import to.ottomot.driftd.core.network.dto.CircleChatDriveAttachmentDto
import to.ottomot.driftd.core.network.dto.CircleChatDriveRoutePointDto
import java.lang.reflect.Type

class CircleChatDriveAttachmentDtoDeserializer :
    JsonDeserializer<CircleChatDriveAttachmentDto?> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?,
    ): CircleChatDriveAttachmentDto? {
        if (json == null || json.isJsonNull) return null
        val obj = json.asJsonObject

        fun bsonIdString(el: JsonElement?): String? {
            if (el == null || el.isJsonNull) return null
            if (el.isJsonPrimitive && el.asJsonPrimitive.isString) {
                return el.asString.trim().takeIf { it.isNotEmpty() }
            }
            if (el.isJsonObject) {
                val oidEl = el.asJsonObject.get("\$oid")
                if (oidEl?.isJsonPrimitive == true && oidEl.asJsonPrimitive.isString) {
                    return oidEl.asString.trim().takeIf { it.isNotEmpty() }
                }
            }
            return null
        }

        fun stringField(o: JsonObject, key: String): String? {
            val el = o.get(key) ?: return null
            if (el.isJsonNull || !el.isJsonPrimitive || !el.asJsonPrimitive.isString) return null
            return el.asString.trim().takeIf { it.isNotEmpty() }
        }

        fun intField(o: JsonObject, key: String): Int? {
            val el = o.get(key) ?: return null
            if (el.isJsonNull || !el.isJsonPrimitive || !el.asJsonPrimitive.isNumber) return null
            return el.asInt
        }

        fun doubleField(o: JsonObject, key: String): Double? {
            val el = o.get(key) ?: return null
            if (el.isJsonNull || !el.isJsonPrimitive || !el.asJsonPrimitive.isNumber) return null
            return el.asDouble
        }

        fun latLngPoints(o: JsonObject, key: String, includeType: Boolean): List<CircleChatDriveRoutePointDto> {
            val arr = o.getAsJsonArray(key) ?: return emptyList()
            return arr.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val p = el.asJsonObject
                val lat = p.get("lat")?.takeIf { it.isJsonPrimitive }?.asDouble ?: return@mapNotNull null
                val lng = p.get("lng")?.takeIf { it.isJsonPrimitive }?.asDouble ?: return@mapNotNull null
                CircleChatDriveRoutePointDto(
                    lat = lat,
                    lng = lng,
                    type = if (includeType) stringField(p, "type") else null,
                )
            }
        }

        val driveId = bsonIdString(obj.get("driveId")) ?: return null
        return CircleChatDriveAttachmentDto(
            driveId = driveId,
            title = stringField(obj, "title"),
            driveTypeLabel = stringField(obj, "driveTypeLabel"),
            completedAt = stringField(obj, "completedAt"),
            distanceMeters = doubleField(obj, "distanceMeters"),
            driveTimeSeconds = intField(obj, "driveTimeSeconds"),
            markerCount = intField(obj, "markerCount"),
            routePoints = latLngPoints(obj, "routePoints", includeType = true),
            roadCoordinates = latLngPoints(obj, "roadCoordinates", includeType = false),
            completedWaypointIndexes =
                obj.getAsJsonArray("completedWaypointIndexes")?.mapNotNull { el ->
                    if (el.isJsonPrimitive && el.asJsonPrimitive.isNumber) el.asInt else null
                } ?: emptyList(),
            parentDeletedAt = stringField(obj, "parentDeletedAt"),
            mapPreviewUrl = stringField(obj, "mapPreviewUrl"),
        )
    }
}
