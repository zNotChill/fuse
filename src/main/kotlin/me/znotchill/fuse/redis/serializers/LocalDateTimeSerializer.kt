package me.znotchill.fuse.redis.serializers

import me.znotchill.fuse.redis.RedisSerializer
import java.time.LocalDateTime

object LocalDateTimeSerializer : RedisSerializer {
    override fun <T> serialize(value: T): String {
        return (value as LocalDateTime).toString()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> deserialize(value: String, type: Class<T>): T {
        return LocalDateTime.parse(value) as T
    }
}