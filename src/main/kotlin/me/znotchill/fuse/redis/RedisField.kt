package me.znotchill.fuse.redis

import io.lettuce.core.api.sync.RedisCommands
import kotlin.reflect.KProperty

class RedisField<T>(
    val key: String,
    val redis: RedisCommands<String, String>,
    val serializer: RedisSerializer,
    val default: T,
    val clazz: Class<T>
) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return redis.get(key)?.let {
            serializer.deserialize(it, clazz)
        } ?: default
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        redis.set(key, serializer.serialize(value))
    }
}