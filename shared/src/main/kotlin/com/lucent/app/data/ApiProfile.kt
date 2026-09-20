package com.lucent.app.data

import org.json.JSONArray
import org.json.JSONObject

data class ApiProfile(
    val name: String = "New API",
    val spec: String = "openai",
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val provider: String = ApiProviders.CUSTOM,
    val selectedModels: List<String> = emptyList()
)

object ApiProfiles {

    const val MAX = 20

    fun serialize(profiles: List<ApiProfile>, encryptKeys: Boolean = true): String {
        val arr = JSONArray()
        profiles.take(MAX).forEach { p ->
            val models = JSONArray()
            p.selectedModels.forEach { models.put(it) }
            arr.put(
                JSONObject()
                    .put("name", p.name)
                    .put("spec", p.spec)
                    .put("baseUrl", p.baseUrl)
                    .put("keyEnc", if (encryptKeys) CryptoUtil.encrypt(p.apiKey) else p.apiKey)
                    .put("model", p.model)
                    .put("provider", p.provider)
                    .put("selectedModels", models)
            )
        }
        return arr.toString()
    }

    fun parse(json: String?): List<ApiProfile> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val spec = o.optString("spec", "openai")
                val baseUrl = o.optString("baseUrl", "")
                val model = o.optString("model", "")
                val stored = o.optJSONArray("selectedModels")
                val selectedModels = if (stored == null) {
                    listOfNotNull(model.trim().takeIf { it.isNotBlank() })
                } else {
                    (0 until stored.length())
                        .map { stored.optString(it) }
                        .filter { it.isNotBlank() }
                        .distinct()
                }
                ApiProfile(
                    name = o.optString("name", "API ${i + 1}"),
                    spec = spec,
                    baseUrl = baseUrl,
                    apiKey = CryptoUtil.decrypt(o.optString("keyEnc", "")),
                    model = model,
                    provider = ApiProviders.resolve(o.optString("provider", ""), spec, baseUrl),
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
