package me.znotchill.fuse.redis.serializers

import me.znotchill.fuse.redis.RedisSerializer

object StringSerializer : RedisSerializer {
    override fun <T> serialize(value: T): String {
        return value.toString()
    }

    override fun <T> deserialize(value: String, type: Class<T>): T {
        return value as T
    }
}