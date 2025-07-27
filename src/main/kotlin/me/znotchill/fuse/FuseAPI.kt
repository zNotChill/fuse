package me.znotchill.fuse

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import me.znotchill.fuse.database.RowRef
import me.znotchill.fuse.redis.RedisField
import me.znotchill.fuse.redis.RedisRow
import me.znotchill.fuse.redis.serializers.IntSerializer
import me.znotchill.fuse.redis.serializers.StringSerializer
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

@Suppress("UNUSED")
class FuseAPI {
    private val logger: Logger = LoggerFactory.getLogger(FuseAPI::class.java)
    lateinit var databaseUrl: String
    lateinit var redisUrl: String
    lateinit var database: Database
    lateinit var redisConnection: StatefulRedisConnection<String, String>

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
            val id = row[table.id]

            row.fieldIndex.forEach { entry ->
                val column = entry.key
                val fieldName = column.toString()
                val fieldValue = row[column]

                if (fieldValue == null) {
                    return@forEach
                }

                val key = getKey(table, id.value)
                api.commands.hset(key, fieldName, fieldValue.toString())
            }
        }
    }

    private fun getKey(table: UUIDTable, uuid: UUID): String {
        return "${table.tableName}:$uuid"
    }

    fun <T : UUIDTable> get(table: T, uuid: UUID): RedisRow {
        return RedisRow(
            table = table,
            uuid = uuid,
            redis = api.commands,
            serializers = mapOf(
                VarCharColumnType::class to StringSerializer,
                IntegerColumnType::class to IntSerializer
            )
        )
    }
}