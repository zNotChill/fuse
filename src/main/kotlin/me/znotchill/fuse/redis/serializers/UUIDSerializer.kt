package me.znotchill.fuse.redis.serializers

import me.znotchill.fuse.redis.RedisSerializer
import java.util.UUID

object UUIDSerializer : RedisSerializer {
    override fun <T> serialize(value: T): String {
        return value.toString()
    }

    override fun <T> deserialize(value: String, type: Class<T>): T {
        return UUID.fromString(value) as T
    }
}