package me.znotchill.fuse.redis.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import me.znotchill.fuse.redis.RedisSerializer

object JsonSerializer : RedisSerializer {
    private val json = Json {
        prettyPrint = true
        isLenient = false
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override fun <T> serialize(value: T): String {
        error("Use serialize(value: T, serializer: KSerializer<T>) instead")
    }

    override fun <T> deserialize(value: String, type: Class<T>): T {
        error("Use deserialize(value: String, serializer: KSerializer<T>) instead")
    }

    fun <T> serialize(value: T, serializer: KSerializer<T>): String {
        return json.encodeToString(serializer, value)
    }

    fun <T> deserialize(value: String, serializer: KSerializer<T>): T {
        return json.decodeFromString(serializer, value)
    }
}