package me.znotchill.fuse.redis

import io.lettuce.core.api.sync.RedisCommands
import kotlinx.serialization.KSerializer
import me.znotchill.fuse.SerializerManager
import me.znotchill.fuse.redis.serializers.JsonSerializer
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.EnumerationColumnType
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
    /**
     * Pushes the current state of the Redis row to the database.
     * Ignores null values and only updates columns that have been set.
     *
     * @return Unit
     */
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

                            is EnumerationColumnType<*> -> {
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

    /**
     * Deletes the specified columns from the Redis row.
     * If no columns are specified, the entire row is deleted.
     * @param columns The columns to delete.
     * @return Unit
     */
    fun delete(vararg columns: Column<*>) {
        transaction {
            val key = "${table.tableName}:$uuid"

            if (columns.isEmpty()) {
                // If no columns are specified, delete the entire row
                redis.del(key)
                return@transaction
            }

            for (column in columns) {
                redis.hdel(key, column.name)
            }
        }
    }

    /**
     * Gets the value of the specified column from the Redis row.
     * Results are cast to the appropriate type based on the column's type.
     *
     * @return The value of the column with the appropriate type, or null if the column does not exist.
     */
    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(column: Column<T>): T? {
        return transaction {
            val key = "${table.tableName}:$uuid"
            val serializedValue = redis.hget(key, column.name)
                ?: return@transaction null

            val columnType = column.columnType
            var (serializer, valueType) = serializers[columnType::class]
                ?: throw IllegalStateException("No serializer registered for type ${columnType::class}")

            if (columnType is EnumerationColumnType<*>) {
                valueType = columnType.klass.java
            }

            return@transaction serializer.deserialize(serializedValue, valueType) as T
        }
    }

    /**
     * Sets the value of the specified column in the Redis row.
     *
     * If the column is of type [JsonBColumnType], it will be serialized using the registered [KSerializer].
     *
     * If the column is of a different type, it will be serialized using the registered [RedisSerializer].
     *
     * If no serializer is registered for the column's type, an [IllegalStateException] will be thrown.
     *
     * @param column The column to set the value for.
     * @param value The value to set for the column. If null, the column will not be set.
     * @return Unit
     * @throws IllegalStateException If no serializer is registered for the column's type or if no [KSerializer] is registered for the value.
      */
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