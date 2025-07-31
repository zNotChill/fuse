package me.znotchill.fuse.redis

import io.lettuce.core.api.sync.RedisCommands
import kotlinx.serialization.KSerializer
import me.znotchill.fuse.SerializerManager
import me.znotchill.fuse.redis.serializers.JsonSerializer
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.EnumerationNameColumnType
import org.jetbrains.exposed.sql.json.JsonBColumnType
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.*
import kotlin.reflect.KClass

class RedisRow(
    val table: UUIDTable,
    val uuid: UUID,
    private val redis: RedisCommands<String, String>,
    private val serializers: Map<KClass<*>, Pair<RedisSerializer, Class<*>>>
) {
    fun pushToDatabase() {
        val row = this
        val table = row.table
        val uuid = row.uuid

        transaction {
            val key = "${table.tableName}:$uuid"
            val fields = redis.hgetall(key)

            table.update({ table.id eq uuid }) { statement ->
                table.columns.forEach { column ->
                    if (column == table.id) return@forEach
                    val columnName = column.name
                    val col = column
                    val serializedValue = fields[columnName]

                    if (serializedValue != null) {
                        var (serializer, valueType) = serializers[column.columnType::class]
                            ?: throw IllegalStateException("No serializer registered for type ${column.columnType::class}")

                        val columnType = column.columnType
                        if (columnType is EnumerationNameColumnType) {
                            // Forcefully change the valueType since we need the enum class,
                            // but the columnType refers to java.lang.Enum
                            @Suppress("UNCHECKED_CAST")
                            valueType = columnType.klass.java
                        }

                        val deserializedValue: Any = when (columnType) {
                            is EnumerationNameColumnType<*> -> {
                                val enumClass = columnType.klass.java as Class<*>
                                serializer.deserialize(serializedValue, enumClass)
                            }

                            is JsonBColumnType -> {
                                columnType.deserialize(serializedValue)
                            }

                            else -> {
                                if (serializedValue == "null") {
                                    // Don't bother serializing null values
                                    return@forEach
                                }
                                serializer.deserialize(serializedValue, valueType)
                            }
                        }

                        @Suppress("UNCHECKED_CAST")
                        // Column<Any> is required but intellij doesn't like it
                        statement[col as Column<Any>] = deserializedValue
                    }
                }
            }
        }
    }

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

            val serializedValue = if (column.columnType is JsonBColumnType) {
                val registeredEntry = SerializerManager.findSerializerFor(value!!)
                    ?: throw IllegalStateException("No KSerializer registered for ${value::class.java}")

                val (serializer, _) = registeredEntry.value

                @Suppress("UNCHECKED_CAST")
                val typedSerializer = serializer as KSerializer<T>

                JsonSerializer.serialize(value, typedSerializer)
            } else {
                val (serializer, _) = serializers[column.columnType::class]
                    ?: throw IllegalStateException("No serializer registered for type ${column.columnType::class}")

                serializer.serialize(value)
            }

            redis.hset(key, column.name, serializedValue)
        }
    }


}