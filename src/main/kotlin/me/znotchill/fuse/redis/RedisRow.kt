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
    private val serializers: Map<KClass<*>, RedisSerializer>
) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(column: Column<T>): T? {
        return transaction {
            val key = "${table.tableName}:$uuid"
            val serializedValue = redis.hget(key, column.toString())
                ?: return@transaction null

            val serializer = serializers[column.columnType::class] ?: throw IllegalStateException(
                "No serializer registered for type ${column.columnType.javaClass}"
            )
            return@transaction serializer.deserialize(serializedValue, column.columnType.javaClass) as T
        }


    }
}