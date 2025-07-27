package me.znotchill.fuse

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import me.znotchill.fuse.Users.testArray
import me.znotchill.fuse.Users.username
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.json.jsonb

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

    val user = api.new(Users) {
        it[username] = "znotchill"
    }

    val redisUser = api.redis.get(Users, user.id)
//    redisUser[testInt] = 200
//    redisUser[testBoolean] = false
//    redisUser[testEnum] = TestEnum.TEST3
    redisUser[testArray] = listOf(
        "a",
        "b",
        "c"
    )
}