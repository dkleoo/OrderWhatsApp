package com.example.bakendorderwhatsapp

import com.example.bakendorderwhatsapp.data.dataBase.configureDatabases
import com.example.bakendorderwhatsapp.di.AppContainer
import com.example.bakendorderwhatsapp.plugins.configureRouting
import com.example.bakendorderwhatsapp.plugins.configureSerialization
import com.example.bakendorderwhatsapp.plugins.configureStatusPages
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureStatusPages()
    configureDatabases()

    val container = AppContainer(environment.config)
    configureRouting(container)
}
