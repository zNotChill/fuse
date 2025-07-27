package me.znotchill.fuse

import me.znotchill.fuse.Users.email
import me.znotchill.fuse.Users.testBoolean
import me.znotchill.fuse.Users.testEnum
import me.znotchill.fuse.Users.testInt
import me.znotchill.fuse.Users.username
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.UUID

enum class TestEnum {
    TEST1, TEST2, TEST3
}

object Users: UUIDTable("users") {
    val username = varchar("name", 30)
    val email = varchar("email", 50).nullable()
    val testInt = integer("test_int").default(0)
    val testBoolean = bool("test_boolean").default(false)
    val testEnum = enumerationByName("test_enum", 20, TestEnum::class).default(TestEnum.TEST1)
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

    val redisUser = api.redis.get(Users, UUID.fromString("b3ecd58f-433c-4698-bdc3-a7208d198ef1"))
    redisUser[testInt] = 200
    redisUser[testBoolean] = false
    redisUser[testEnum] = TestEnum.TEST3
}