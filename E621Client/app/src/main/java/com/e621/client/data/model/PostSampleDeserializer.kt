package com.e621.client.data.model

import com.google.gson.*
import java.lang.reflect.Type

/**
 * Custom deserializer for PostSample to handle the 'alternates' field
 * which can be either a JSON object or false (boolean) when empty
 */
class PostSampleDeserializer : JsonDeserializer<PostSample> {
    
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): PostSample {
        val jsonObject = json.asJsonObject
        
        // Safely get values, handling JsonNull
        val has = jsonObject.get("has")?.let { 
            if (it.isJsonNull) null else it.asBoolean 
        }
        val width = jsonObject.get("width")?.let { 
            if (it.isJsonNull) null else it.asInt 
        }
        val height = jsonObject.get("height")?.let { 
            if (it.isJsonNull) null else it.asInt 
        }
        val url = jsonObject.get("url")?.let { 
            if (it.isJsonNull) null else it.asString 
        }
        
        // Handle alternates field - can be false (boolean), null, or object
        var alternates: Map<String, SampleAlternate>? = null
        
        val alternatesElement = jsonObject.get("alternates")
        if (alternatesElement != null && !alternatesElement.isJsonNull && alternatesElement.isJsonObject) {
            val alternatesObj = alternatesElement.asJsonObject
            val map = mutableMapOf<String, SampleAlternate>()
            
            for ((key, value) in alternatesObj.entrySet()) {
                if (value.isJsonObject) {
                    try {
                        val alternate = context.deserialize<SampleAlternate>(value, SampleAlternate::class.java)
                        map[key] = alternate
                    } catch (e: Exception) {
                        // Skip invalid entries
                    }
                }
            }
            
            if (map.isNotEmpty()) {
                alternates = map
            }
        }
        // If alternates is false, null, or not an object, it stays null
        
        return PostSample(has, width, height, url, alternates)
    }
}
