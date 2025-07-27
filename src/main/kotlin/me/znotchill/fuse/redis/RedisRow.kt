package me.znotchill.fuse.redis

import io.lettuce.core.api.sync.RedisCommands
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.BooleanColumnType
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.EnumerationNameColumnType
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.VarCharColumnType
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

            val columnType = column.columnType
            val serializer = serializers[columnType::class] ?: throw IllegalStateException(
                "No serializer registered for type ${column.columnType.javaClass}"
            )

            val valueType: Class<*> = when (columnType) {
                is EnumerationNameColumnType<*> -> columnType.klass.java
                is VarCharColumnType -> String::class.java
                is IntegerColumnType -> Int::class.java
                is BooleanColumnType -> Boolean::class.java
                else -> throw IllegalStateException("Unsupported column type: $columnType")
            }

            return@transaction serializer.deserialize(serializedValue, valueType) as T
        }
    }

    operator fun <T : Any> set(column: Column<T>, value: T) {
        transaction {
            val key = "${table.tableName}:$uuid"
            val serializer = serializers[column.columnType::class]
                ?: throw IllegalStateException("No serializer registered for type ${column.columnType.javaClass}")

            val serializedValue = serializer.serialize(value)
            redis.hset(key, column.toString(), serializedValue)
        }
    }
}