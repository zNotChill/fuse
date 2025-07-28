package me.znotchill.fuse

import kotlinx.serialization.KSerializer
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.json.JsonBColumnType

fun <T> Column<T>.registerSerializer(
    serializer: KSerializer<T>,
    classType: Class<*>
): Column<T> {
    if (this.columnType is JsonBColumnType) {
        SerializerManager.registerKSerializer(
            classType.kotlin,
            serializer,
            classType
        )
    }

    return this
}