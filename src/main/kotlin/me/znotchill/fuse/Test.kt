package me.znotchill.fuse

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.json.jsonb
import java.util.*

enum class TestEnum {
    TEST1, TEST2, TEST3
}

@Serializable
data class TestClass(
    val test: String,
    val test2: Int,
    val test3: Boolean,
)

object Users: UUIDTable("users") {
    val json = Json {
        prettyPrint = true
        isLenient = false
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    val username = varchar("name", 30)
    val email = varchar("email", 50).nullable()
    val testInt = integer("test_int").default(0)
    val testBoolean = bool("test_boolean").default(false)
    val testEnum = enumerationByName("test_enum", 20, TestEnum::class).default(TestEnum.TEST1)

    val long = long("test_long").default(0L)
    val double = double("test_double").default(0.0)
    val decimal = decimal("test_decimal", 10, 2).default("0.00".toBigDecimal())
    val text = text("test_text").default("")
    val date = date("test_date").default(java.time.LocalDate.now())
    val dateTime = datetime("test_datetime").default(java.time.LocalDateTime.now())
    val uuid = uuid("test_uuid").default(java.util.UUID.randomUUID())
    val binary = binary("test_binary").default(ByteArray(0))

    val testJson = jsonb<TestClass>("test_json", Json {
        prettyPrint = true
        isLenient = false
        ignoreUnknownKeys = true
        coerceInputValues = true
    }, TestClass.serializer())
        .default(TestClass("default", 0, false))
        .registerSerializer(
            TestClass.serializer(),
            TestClass::class.java
        )

    val testArray = jsonb<List<TestClass>>("test_array", json,
        ListSerializer(TestClass.serializer())
    )
        .default(emptyList())
        .registerSerializer(
            ListSerializer(TestClass.serializer()),
            List::class.java
        )
}

fun main() {
    val api = FuseAPI()
    api.connect(
        databaseUrl = "jdbc:postgresql://localhost:15432/evbopk",
        databaseDriver = "org.postgresql.Driver",
        databaseUser = "user",
        databasePassword = "password",
        redisHost = "localhost",
        redisPort = 6379,
        redisUser = "default",
        redisPassword = "yourpassword"
    )

    api.createTable(Users)

    val redisUser = api.redis.get(Users, UUID.fromString("33558632-4eeb-4e98-b44d-ad94d04d121b"))
    redisUser[Users.testInt] = 200
    redisUser[Users.testBoolean] = false
    redisUser[Users.testEnum] = TestEnum.TEST3
    redisUser[Users.testArray] = listOf(
        TestClass("test1", 123, true),
        TestClass("test2", 256, false),
        TestClass("test333", 31233, true)
    )
    redisUser[Users.testJson] = TestClass("test123123", 121233, true)
    redisUser[Users.long] = 123456789L
    redisUser[Users.double] = 3.14
    redisUser[Users.decimal] = "123123.45".toBigDecimal()
    redisUser[Users.text] = "This is a test text"
    redisUser[Users.date] = java.time.LocalDate.of(20233, 10, 1)
    redisUser[Users.dateTime] = java.time.LocalDateTime.of(2023, 10, 1, 12, 0)
    redisUser[Users.uuid] = UUID.randomUUID()
    redisUser[Users.binary] = "This is a test binary".toByteArray()
    redisUser.pushToDatabase()

}