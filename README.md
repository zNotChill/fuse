# Fuse
A small library to link Redis and Exposed.

## Usage
You still need to import exposed as normal, as well as lettuce (custom Redis drivers will be allowed soon)

### Supported
- varchars
- integers
- booleans
- enumerations
- longs
- doubles
- decimals/big decimals
- text
- dates
- datetimes
- uuids
- binary
- jsonb

```kt
implementation("com.github.zNotChill:fuse:v1.0.0")
```

Define a table as normal:
```kt
object Users : UUIDTable("users") {
	val username = varchar("username", 30)
	val email = varchar("email", 50).nullable()
	val dateJoined = datetime("date_joined").default(
		java.time.LocalDateTime.now()
	)
}
```
Enums function as normal:
```kt  
val testEnum = enumerationByName(
	"test_enum",
	20,
	TestEnum::class
).default(TestEnum.TEST1)
```
JSON requires an extra step in order to function:
You have to use `registerSerializer()` in order for the JSON to be able to serialize and deserialize properly from Redis.

Array example:
```kt
val json = Json {  
    prettyPrint = true  
    isLenient = false  
    ignoreUnknownKeys = true  
    coerceInputValues = true  
}
val testArray = jsonb<List<TestClass>>(
	"test_array",
	json,  
    ListSerializer(TestClass.serializer())
)
	.default(emptyList())
	.registerSerializer(  
		ListSerializer(TestClass.serializer()),  
		List::class.java
	)
```
Object example:
```kt
val json = Json {  
    prettyPrint = true  
    isLenient = false  
    ignoreUnknownKeys = true  
    coerceInputValues = true  
}
val testJson = jsonb<TestClass>(
	"test_json",
	json,
	TestClass.serializer()
)  
    .default(
		TestClass("default", 0, false)
    )  
    .registerSerializer(  
        TestClass.serializer(),  
        TestClass::class.java  
    )
```

Once you have a table set up, that is all the Exposed work done, and the rest is handled by the program.

### API Setup
```kt
val api = FuseAPI()  
api.connect(  
    databaseUrl = "",
    databaseDriver = "org.postgresql.Driver", // or any installed driver  
    databaseUser = "user",
    databasePassword = "password",
    redisHost = "localhost",
    redisPort = 6379,
    redisUser = "default",
    redisPassword = "yourpassword"
)
```
Then, you can register your table: `api.createTable(Users)`

### Inserting
Inserting something into the database is very simple.
```kt
val user = api.new(Users) {
    it[Users.username] = "testuser"
}
```
You can select the Redis data for the insertion, since it is automatically created when a new row is inserted:
```kt
val redisUser = api.redis.get(Users, user.id)
```
You can modify values through Redis, which is recommended over doing it directly through the database, since that may cause some issues right now.
You can modify any value:
```kt
redisUser[Users.username] = "newuser"  
redisUser[Users.dateTime] = java.time.LocalDateTime.of(2023, 10, 1, 12, 0)
redisUser[Users.email] = "test@user.com"
```
JSON and Enums also work exactly how you'd expect them to:
```kt
redisUser[Users.testEnum] = TestEnum.TEST3
redisUser[Users.testArray] = listOf(
    TestClass("test1", 123, true),
    TestClass("test2", 456, false),
    TestClass("test3", 789, true)
)
redisUser[Users.testJson] = TestClass("test", 123, true)
```
Then, you can push the Redis data back into the database:
```kt
redisUser.pushToDatabase()
```
