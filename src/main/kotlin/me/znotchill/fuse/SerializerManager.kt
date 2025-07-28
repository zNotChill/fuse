package me.znotchill.fuse

import kotlinx.serialization.KSerializer
import me.znotchill.fuse.redis.RedisSerializer
import me.znotchill.fuse.redis.serializers.BinarySerializer
import me.znotchill.fuse.redis.serializers.BoolSerializer
import me.znotchill.fuse.redis.serializers.DecimalSerializer
import me.znotchill.fuse.redis.serializers.DoubleSerializer
import me.znotchill.fuse.redis.serializers.EnumSerializer
import me.znotchill.fuse.redis.serializers.IntSerializer
import me.znotchill.fuse.redis.serializers.JsonSerializer
import me.znotchill.fuse.redis.serializers.LocalDateSerializer
import me.znotchill.fuse.redis.serializers.LocalDateTimeSerializer
import me.znotchill.fuse.redis.serializers.LongSerializer
import me.znotchill.fuse.redis.serializers.StringSerializer
import me.znotchill.fuse.redis.serializers.UUIDSerializer
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.BasicBinaryColumnType
import org.jetbrains.exposed.sql.BooleanColumnType
import org.jetbrains.exposed.sql.DecimalColumnType
import org.jetbrains.exposed.sql.DoubleColumnType
import org.jetbrains.exposed.sql.EntityIDColumnType
import org.jetbrains.exposed.sql.EnumerationNameColumnType
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.LongColumnType
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.UUIDColumnType
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.javatime.JavaLocalDateColumnType
import org.jetbrains.exposed.sql.javatime.JavaLocalDateTimeColumnType
import org.jetbrains.exposed.sql.jodatime.DateColumnType
import org.jetbrains.exposed.sql.json.JsonBColumnType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.reflect.KClass

object SerializerManager {
    val serializers: Map<KClass<*>, Pair<RedisSerializer, Class<*>>> = mapOf(
        VarCharColumnType::class to (StringSerializer to String::class.java),
        EntityIDColumnType::class to (UUIDSerializer to EntityID::class.java),
        IntegerColumnType::class to (IntSerializer to Int::class.java),
        BooleanColumnType::class to (BoolSerializer to Boolean::class.java),
        EnumerationNameColumnType::class to (EnumSerializer to Enum::class.java),
        JsonBColumnType::class to (JsonSerializer to Any::class.java),
        LongColumnType::class to (LongSerializer to Long::class.java),
        DoubleColumnType::class to (DoubleSerializer to Double::class.java),
        DecimalColumnType::class to (DecimalSerializer to BigDecimal::class.java),
        TextColumnType::class to (StringSerializer to String::class.java),
        DateColumnType::class to (LocalDateSerializer to LocalDate::class.java),
        JavaLocalDateColumnType::class to (LocalDateSerializer to LocalDate::class.java),
        JavaLocalDateTimeColumnType::class to (LocalDateTimeSerializer to LocalDateTime::class.java),
        UUIDColumnType::class to (UUIDSerializer to UUID::class.java),
        BasicBinaryColumnType::class to (BinarySerializer to ByteArray::class.java),
    )

    var kSerializers: MutableMap<KClass<*>, Pair<KSerializer<*>, Class<*>>> = mutableMapOf()

    fun registerKSerializer(
        columnType: KClass<*>,
        serializer: KSerializer<*>,
        valueType: Class<*>
    ) {
        kSerializers[columnType] = Pair(serializer, valueType)
    }

    fun findSerializerFor(value: Any): Map.Entry<KClass<*>, Pair<KSerializer<*>, Class<*>>>? {
        val klass = value::class
        kSerializers[klass]?.let { return kSerializers.entries.first { it.key == klass } }

        return kSerializers.entries.firstOrNull { it.key.java.isAssignableFrom(klass.java) }
    }
}