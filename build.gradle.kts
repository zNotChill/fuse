plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
}

group = "me.znotchill"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val exposedVersion = "0.61.0"
dependencies {
    testImplementation(kotlin("test"))

    implementation("org.jetbrains.exposed:exposed-core:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-crypt:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-dao:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-jdbc:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-jodatime:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-java-time:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-json:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-money:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-spring-boot-starter:${exposedVersion}")

    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("io.lettuce:lettuce-core:6.3.0.RELEASE")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}