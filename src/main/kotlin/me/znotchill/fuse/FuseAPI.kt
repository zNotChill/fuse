package me.znotchill.fuse

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import me.znotchill.fuse.database.RowRef
import me.znotchill.fuse.redis.RedisField
import me.znotchill.fuse.redis.RedisRow
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

@Suppress("UNUSED")
class FuseAPI {
    private val logger: Logger = LoggerFactory.getLogger(FuseAPI::class.java)
    lateinit var databaseUrl: String
    lateinit var redisUrl: String
    lateinit var database: Database
    lateinit var redisConnection: StatefulRedisConnection<String, String>

    val serializers = SerializerManager.serializers

    var redis: FuseRedisAPI = FuseRedisAPI(this)

    val caches: MutableMap<String, MutableMap<String, RedisField<*>>> = mutableMapOf()

    val commands: RedisCommands<String, String>
        get() = redisConnection.sync() ?:
            throw IllegalStateException("Redis connection is not established. Please call connect() first.")

    /**
     * Connects to the database and Redis server.
     *
     * @param databaseUrl The URL of the database to connect to.
     * @param databaseUser The username for the database.
     * @param databasePassword The password for the database.
     * @param databaseDriver The driver class name for the database. (example: "org.postgresql.Driver")
     * @param redisHost The host of the Redis server.
     * @param redisPort The port of the Redis server.
     * @param redisUser The username for the Redis server.
     * @param redisPassword The password for the Redis server.
     *
     * @return True if the connection was successful, false otherwise.
     */
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

    /**
     * Creates a table in the database.
     *
     * @param table The table to create.
     */
    fun createTable(table: Table) {
        transaction { SchemaUtils.create(table) }
        val tableName = table.tableName

        logger.info("Table $tableName created.")
    }

    /**
     * Creates a new row in the specified table.
     *
     * @return [RowRef] representing the newly created row.
     */
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

    /**
     * Retrieves a row from the specified table by its UUID id.
     * If the row does not exist, returns null.
     *
     * @param table The table to retrieve the row from.
     * @param id The UUID of the row to retrieve.
     * @return The [ResultRow] if found, or null if not found.
     */
    fun get(table: UUIDTable, id: UUID): ResultRow? = transaction {
        table.selectAll()
            .where { table.id eq id }
            .firstOrNull()
    }

    /**
     * Selects a row from the specified table based on a predicate.
     * If no rows match the predicate, returns null.
     *
     * @param table The table to select from.
     * @param predicate The predicate to filter rows.
     * @return The first matching [ResultRow] or null if no match is found.
     */
    fun select(
        table: UUIDTable,
        predicate: SqlExpressionBuilder.() -> Op<Boolean>
    ): ResultRow? = transaction {
        table.selectAll()
            .where(predicate)
            .limit(1)
            .firstOrNull()
    }

    /**
     * Checks if a row exists in the specified table based on a predicate.
     * If no rows match the predicate, returns false.
     *
     * @param table The table to check.
     * @param predicate The predicate to filter rows.
     * @return True if at least one row matches the predicate, false otherwise.
     */
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
    /**
     * Populates a Redis row with data from a database row.
     * This method is used to synchronize data between the database and Redis.
     *
     * @param table The table from which the row is being populated.
     * @param row The database row containing the data to populate.
     * @return Unit
     */
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

    /**
     * Retrieves a Redis row for the specified table and UUID.
     * This method allows you to interact with a specific row in Redis,
     * providing methods to get, set, and delete fields.
     *
     * @param table The table to which the row belongs.
     * @param uuid The UUID of the row to retrieve.
     * @return A [RedisRow] instance representing the row in Redis.
     */
    fun <T : UUIDTable> get(table: T, uuid: UUID): RedisRow {
        return RedisRow(
            table = table,
            uuid = uuid,
            redis = api.commands,
            serializers = api.serializers
        )
    }
}