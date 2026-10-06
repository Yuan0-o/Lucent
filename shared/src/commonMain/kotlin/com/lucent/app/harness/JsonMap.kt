package com.lucent.app.harness

import kotlinx.serialization.json.*

class JsonMap {
    val map = mutableMapOf<String, JsonElement>()

    fun put(key: String, value: String?): JsonMap {
        map[key] = if (value == null) JsonNull else JsonPrimitive(value)
        return this
    }

    fun put(key: String, value: Number?): JsonMap {
        map[key] = if (value == null) JsonNull else JsonPrimitive(value)
        return this
    }

    fun put(key: String, value: Boolean?): JsonMap {
        map[key] = if (value == null) JsonNull else JsonPrimitive(value)
        return this
    }

    fun put(key: String, value: JsonElement?): JsonMap {
        map[key] = value ?: JsonNull
        return this
    }

    fun put(key: String, value: JsonMap): JsonMap {
        map[key] = value.toJsonObject()
        return this
    }

    fun put(key: String, value: JsonList): JsonMap {
        map[key] = value.toJsonArray()
        return this
    }

    fun toJsonObject(): JsonObject = JsonObject(map)

    override fun toString(): String = toJsonObject().toString()
}

class JsonList {
    val list = mutableListOf<JsonElement>()

    fun put(value: String?): JsonList {
        list.add(if (value == null) JsonNull else JsonPrimitive(value))
        return this
    }

    fun put(value: Number?): JsonList {
        list.add(if (value == null) JsonNull else JsonPrimitive(value))
        return this
    }

    fun put(value: Boolean?): JsonList {
        list.add(if (value == null) JsonNull else JsonPrimitive(value))
        return this
    }

    fun put(value: JsonElement?): JsonList {
        list.add(value ?: JsonNull)
        return this
    }

    fun put(value: JsonMap): JsonList {
        list.add(value.toJsonObject())
        return this
    }

    fun put(value: JsonList): JsonList {
        list.add(value.toJsonArray())
        return this
    }

    fun toJsonArray(): JsonArray = JsonArray(list)

    override fun toString(): String = toJsonArray().toString()
}
