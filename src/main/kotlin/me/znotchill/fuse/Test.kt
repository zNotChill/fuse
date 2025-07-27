package me.znotchill.fuse

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import me.znotchill.fuse.Users.binary
import me.znotchill.fuse.Users.date
import me.znotchill.fuse.Users.dateTime
import me.znotchill.fuse.Users.decimal
import me.znotchill.fuse.Users.double
import me.znotchill.fuse.Users.long
import me.znotchill.fuse.Users.username
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

    val testArray = jsonb<List<String>>("test_array", Json {
        prettyPrint = true
        isLenient = false
        ignoreUnknownKeys = true
        coerceInputValues = true
    }, ListSerializer(String.serializer()))
        .default(emptyList())
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

//    val user = api.new(Users) {
//        it[username] = "znotchill"
//    }

    val redisUser = api.redis.get(Users, UUID.fromString("33558632-4eeb-4e98-b44d-ad94d04d121b"))
    println(redisUser[binary])
    println(redisUser[binary]?.contentToString())
    println(redisUser[date])
    println(redisUser[dateTime])
    println(redisUser[decimal])
    println(redisUser[double])
    println(redisUser[long])
    println(redisUser[username])

//    redisUser[testInt] = 200
//    redisUser[testBoolean] = false
//    redisUser[testEnum] = TestEnum.TEST3
//    redisUser[testArray] = listOf(
//        "a",
//        "b",
//        "c"
//    )
//    redisUser[testJson] = TestClass("test", 123, true)
//    redisUser[long] = 123456789L
//    redisUser[double] = 3.14
//    redisUser[decimal] = "123.45".toBigDecimal()
//    redisUser[text] = "This is a test text"
//    redisUser[date] = java.time.LocalDate.of(2023, 10, 1)
//    redisUser[dateTime] = java.time.LocalDateTime.of(2023, 10, 1, 12, 0)
//    redisUser[uuid] = java.util.UUID.randomUUID()
//    redisUser[binary] = "This is a test binary".toByteArray()
}