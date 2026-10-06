package com.lucent.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class ApiProfile(
    @SerialName("name") val name: String = "New API",
    @SerialName("spec") val spec: String = "openai",
    @SerialName("baseUrl") val baseUrl: String = "",
    @SerialName("keyEnc") val apiKey: String = "",
    @SerialName("model") val model: String = "",
    @SerialName("provider") val provider: String = ApiProviders.CUSTOM,
    @SerialName("selectedModels") val selectedModels: List<String> = emptyList()
)

object ApiProfiles {

    const val MAX = 20

    private val jsonConfig = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun serialize(profiles: List<ApiProfile>, encryptKeys: Boolean = true): String {
        val toSerialize = profiles.take(MAX).map { p ->
            if (encryptKeys) p.copy(apiKey = CryptoUtil.encrypt(p.apiKey)) else p
        }
        return jsonConfig.encodeToString(toSerialize)
    }

    fun parse(jsonString: String?): List<ApiProfile> {
        if (jsonString.isNullOrBlank()) return emptyList()
        return try {
            val arr = jsonConfig.decodeFromString<JsonArray>(jsonString)
            arr.indices.mapNotNull { i ->
                val o = arr[i] as? JsonObject ?: return@mapNotNull null
                val spec = (o["spec"] as? JsonPrimitive)?.content ?: "openai"
                val baseUrl = (o["baseUrl"] as? JsonPrimitive)?.content ?: ""
                val model = (o["model"] as? JsonPrimitive)?.content ?: ""
                val stored = o["selectedModels"] as? JsonArray
                val selectedModels = if (stored == null) {
                    listOfNotNull(model.trim().takeIf { it.isNotBlank() })
                } else {
                    stored.indices
                        .mapNotNull { j -> (stored[j] as? JsonPrimitive)?.content }
                        .filter { it.isNotBlank() }
                        .distinct()
                }
                ApiProfile(
                    name = (o["name"] as? JsonPrimitive)?.content ?: "API ${i + 1}",
                    spec = spec,
                    baseUrl = baseUrl,
                    apiKey = CryptoUtil.decrypt((o["keyEnc"] as? JsonPrimitive)?.content ?: ""),
                    model = model,
                    provider = ApiProviders.resolve((o["provider"] as? JsonPrimitive)?.content ?: "", spec, baseUrl),
                    selectedModels = selectedModels
                )
            }.take(MAX)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun serializeForBackup(profiles: List<ApiProfile>): String = serialize(profiles, encryptKeys = true)

    fun nextDefaultName(existing: List<ApiProfile>): String {
        val taken = existing.mapNotNull { p ->
            Regex("^API (\\d+)$").find(p.name.trim())?.groupValues?.get(1)?.toIntOrNull()
        }.toSet()
        var n = 1
        while (n in taken) n++
        return "API $n"
    }
}
