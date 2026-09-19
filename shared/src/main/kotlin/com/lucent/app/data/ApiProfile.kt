package com.lucent.app.data

import org.json.JSONArray
import org.json.JSONObject

data class ApiProfile(
    val name: String = "New API",
    val spec: String = "openai",
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = ""
)

object ApiProfiles {

    const val MAX = 5

    fun serialize(profiles: List<ApiProfile>, encryptKeys: Boolean = true): String {
        val arr = JSONArray()
        profiles.take(MAX).forEach { p ->
            arr.put(
                JSONObject()
                    .put("name", p.name)
                    .put("spec", p.spec)
                    .put("baseUrl", p.baseUrl)
                    .put("keyEnc", if (encryptKeys) CryptoUtil.encrypt(p.apiKey) else p.apiKey)
                    .put("model", p.model)
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
                ApiProfile(
                    name = o.optString("name", "API ${i + 1}"),
                    spec = o.optString("spec", "openai"),
                    baseUrl = o.optString("baseUrl", ""),
                    apiKey = CryptoUtil.decrypt(o.optString("keyEnc", "")),
                    model = o.optString("model", "")
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
