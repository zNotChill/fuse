package me.znotchill.fuse

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import me.znotchill.fuse.database.RowRef
import me.znotchill.fuse.redis.RedisField
import me.znotchill.fuse.redis.RedisRow
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
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.BasicBinaryColumnType
import org.jetbrains.exposed.sql.BinaryColumnType
import org.jetbrains.exposed.sql.BooleanColumnType
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DecimalColumnType
import org.jetbrains.exposed.sql.DoubleColumnType
import org.jetbrains.exposed.sql.EntityIDColumnType
import org.jetbrains.exposed.sql.EnumerationNameColumnType
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.LongColumnType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.UUIDColumnType
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.javatime.JavaLocalDateColumnType
import org.jetbrains.exposed.sql.javatime.JavaLocalDateTimeColumnType
import org.jetbrains.exposed.sql.jodatime.DateColumnType
import org.jetbrains.exposed.sql.jodatime.DateTimeWithTimeZoneColumnType
import org.jetbrains.exposed.sql.json.JsonBColumnType
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.reflect.KClass

@Suppress("UNUSED")
class FuseAPI {
    private val logger: Logger = LoggerFactory.getLogger(FuseAPI::class.java)
    lateinit var databaseUrl: String
    lateinit var redisUrl: String
    lateinit var database: Database
    lateinit var redisConnection: StatefulRedisConnection<String, String>

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

    var redis: FuseRedisAPI = FuseRedisAPI(this)

    val caches: MutableMap<String, MutableMap<String, RedisField<*>>> = mutableMapOf()

    val commands: RedisCommands<String, String>
        get() = redisConnection.sync() ?:
            throw IllegalStateException("Redis connection is not established. Please call connect() first.")

    fun connect(
        databaseUrl: String,
        databaseUser: String,
        databasePassword: String,
        databaseDriver: String,
        redisHost: String,
        redisPort: Int,
        redisUser: String,
        redisPassword: String
    ): Boolean {
        logger.info("Starting FuseAPI connection...")
        this.databaseUrl = databaseUrl
        this.redisUrl = "redis://$redisHost:$redisPort"

        try {
            val redisUri = RedisURI.Builder.redis(redisHost, redisPort)
                .withAuthentication(redisUser, redisPassword)
                .build()

            val client = RedisClient.create(redisUri)
            this.redisConnection = client.connect()

            this.database = Database.connect(
                url = databaseUrl,
                user = databaseUser,
                password = databasePassword,
                driver = databaseDriver
            )

            logger.info("Successfully connected to database and Redis.")
        } catch (e: Exception) {
            logger.error("Failed to connect to database or Redis: ${e.message}", e)
            return false
        }

        return true
    }

    fun createTable(table: Table) {
        transaction { SchemaUtils.create(table) }
        val tableName = table.tableName

        logger.info("Table $tableName created.")
    }

    fun new(table: UUIDTable, init: UUIDTable.(InsertStatement<EntityID<UUID>>) -> Unit): RowRef {
        val id = transaction {
            table.insertAndGetId { row ->
                init(row)
            }.value
        }

        val row = get(table, id)
            ?: throw IllegalStateException("Failed to retrieve row after insertion.")

        redis.populate(table, row)

        return RowRef(table, id)
    }

    fun get(table: UUIDTable, id: UUID): ResultRow? = transaction {
        table.selectAll()
            .where { table.id eq id }
            .firstOrNull()
    }

    fun select(
        table: UUIDTable,
        predicate: SqlExpressionBuilder.() -> Op<Boolean>
    ): ResultRow? = transaction {
        table.selectAll()
            .where(predicate)
            .limit(1)
            .firstOrNull()
    }

    fun exists(
        table: UUIDTable,
        predicate: SqlExpressionBuilder.() -> Op<Boolean>
    ): Boolean = transaction {
        table.selectAll()
            .where(predicate)
            .count() > 0
    }
}

class FuseRedisAPI(
    val api: FuseAPI
) {

    fun populate(table: UUIDTable, row: ResultRow) {
        transaction {
            val uuid = row[table.id].value

            val redisRow = RedisRow(
                table = table,
                uuid = uuid,
                redis = api.commands,
                serializers = api.serializers
            )

            table.columns.forEach { column ->
                val value = row[column]

                @Suppress("UNCHECKED_CAST")
                redisRow[column as Column<Any>] = value
            }
        }
    }

    fun <T : UUIDTable> get(table: T, uuid: UUID): RedisRow {
        return RedisRow(
            table = table,
            uuid = uuid,
            redis = api.commands,
            serializers = api.serializers
        )
    }
}