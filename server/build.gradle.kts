plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
}

group = "com.example.bakendorderwhatsapp"
version = "1.0.0"
application {
    mainClass = "com.example.bakendorderwhatsapp.ApplicationKt"
}

dependencies {
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serializationKotlinxJson)
    implementation(libs.ktor.serverStatusPages)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.hikari)
    implementation(libs.postgresql)

    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}
