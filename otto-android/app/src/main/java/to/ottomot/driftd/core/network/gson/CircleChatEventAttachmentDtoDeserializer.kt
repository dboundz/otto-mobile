package to.ottomot.driftd.core.network.gson

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import to.ottomot.driftd.core.network.dto.CircleChatEventAttachmentDto
import java.lang.reflect.Type

/**
 * API may send Mongo-style `{"eventId": {"$oid":"..."}}` or a plain hex string — match iOS decoding.
 */
class CircleChatEventAttachmentDtoDeserializer :
    JsonDeserializer<CircleChatEventAttachmentDto?> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?,
    ): CircleChatEventAttachmentDto? {
        if (json == null || json.isJsonNull) return null
        val obj = json.asJsonObject

        fun stringField(o: JsonObject, key: String): String? {
            val el = o.get(key) ?: return null
            if (el.isJsonNull || !el.isJsonPrimitive || !el.asJsonPrimitive.isString) return null
            val t = el.asString.trim()
            return t.takeIf { it.isNotEmpty() }
        }

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

        val eventId = bsonIdString(obj.get("eventId")) ?: return null
        return CircleChatEventAttachmentDto(
            eventId = eventId,
            name = stringField(obj, "name"),
            startsAt = stringField(obj, "startsAt"),
            addressLabel = stringField(obj, "addressLabel"),
            bannerImageUrl = stringField(obj, "bannerImageUrl"),
            visibility = stringField(obj, "visibility"),
            circleId = bsonIdString(obj.get("circleId")),
            parentDeletedAt = stringField(obj, "parentDeletedAt"),
        )
    }
}
