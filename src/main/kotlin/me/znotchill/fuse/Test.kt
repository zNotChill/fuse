package me.znotchill.fuse

import me.znotchill.fuse.Users.email
import me.znotchill.fuse.Users.testInt
import me.znotchill.fuse.Users.username
import org.jetbrains.exposed.dao.id.UUIDTable

object Users: UUIDTable("users") {
    val username = varchar("name", 30)
    val email = varchar("email", 50).nullable()
    val testInt = integer("test_int").default(0)
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
        it[email] = ""
        it[testInt] = 42
    }

    val redisUser = api.redis.get(Users, user.id)
    println(redisUser[username])
    println(redisUser[testInt])
}