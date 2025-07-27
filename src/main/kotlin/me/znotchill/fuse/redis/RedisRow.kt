package me.znotchill.fuse.redis

import io.lettuce.core.api.sync.RedisCommands
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.reflect.KClass

class RedisRow(
    private val table: UUIDTable,
    private val uuid: UUID,
    private val redis: RedisCommands<String, String>,
    private val serializers: Map<KClass<*>, Pair<RedisSerializer, Class<*>>>
) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(column: Column<T>): T? {
        return transaction {
            val key = "${table.tableName}:$uuid"
            val serializedValue = redis.hget(key, column.name)
                ?: return@transaction null

            val columnType = column.columnType
            val (serializer, valueType) = serializers[columnType::class]
                ?: throw IllegalStateException("No serializer registered for type ${columnType::class}")

            return@transaction serializer.deserialize(serializedValue, valueType) as T
        }
    }

    operator fun <T : Any> set(column: Column<T>, value: T?) {
        transaction {
            val key = "${table.tableName}:$uuid"
            val (serializer, _) = serializers[column.columnType::class]
                ?: throw IllegalStateException("No serializer registered for type ${column.columnType::class}")

            val serializedValue = serializer.serialize(value)
            redis.hset(key, column.name, serializedValue)
        }
    }

}