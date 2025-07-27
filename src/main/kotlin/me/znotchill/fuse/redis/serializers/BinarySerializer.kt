package me.znotchill.fuse.redis.serializers

import me.znotchill.fuse.redis.RedisSerializer
import java.util.Base64
import java.util.UUID

object BinarySerializer : RedisSerializer {
    override fun <T> serialize(value: T): String {
        val bytes = value as? ByteArray ?:
            throw IllegalArgumentException("Value must be a ByteArray")

        return Base64.getEncoder().encodeToString(bytes)
    }

    override fun <T> deserialize(value: String, type: Class<T>): T {
        val bytes = Base64.getDecoder().decode(value)
        return bytes as T
    }
}