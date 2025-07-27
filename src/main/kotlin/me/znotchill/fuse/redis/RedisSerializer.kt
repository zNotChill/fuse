package me.znotchill.fuse.redis

interface RedisSerializer {
    fun <T> serialize(value: T): String

    fun <T> deserialize(value: String, type: Class<T>): T
}