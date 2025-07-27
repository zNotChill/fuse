package me.znotchill.fuse.redis.serializers

import me.znotchill.fuse.redis.RedisSerializer

object EnumSerializer : RedisSerializer {
    override fun <T> serialize(value: T): String {
        return value.toString()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> deserialize(value: String, type: Class<T>): T {
        if (type.isEnum) {
            return java.lang.Enum.valueOf(type as Class<out Enum<*>>, value) as T
        }
        throw IllegalArgumentException("Type $type is not an enum.")
    }
}