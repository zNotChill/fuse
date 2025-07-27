package me.znotchill.fuse.redis.serializers

import com.google.gson.Gson
import me.znotchill.fuse.redis.RedisSerializer

object JsonSerializer : RedisSerializer {
    private val gson = Gson()

    override fun <T> serialize(value: T): String {
        return gson.toJson(value)
    }

    override fun <T> deserialize(value: String, type: Class<T>): T {
        return gson.fromJson(value, type)
    }
}