package net.derfruhling.spacemaven

import com.google.cloud.datastore.DatastoreOptions
import io.ktor.server.application.*

val datastore = DatastoreOptions.newBuilder()
    .setProjectId("spacemaven")
    .build().service!!

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSecurity()
    configureRouting()
}
