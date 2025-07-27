package me.znotchill.fuse.redis.serializers

import me.znotchill.fuse.redis.RedisSerializer
import java.time.LocalDate

object LocalDateSerializer : RedisSerializer {
    override fun <T> serialize(value: T): String {
        return (value as LocalDate).toString()
    }

    override fun <T> deserialize(value: String, type: Class<T>): T {
        return LocalDate.parse(value) as T
    }
}