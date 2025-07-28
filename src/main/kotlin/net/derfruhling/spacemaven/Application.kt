package net.derfruhling.spacemaven

import com.google.auth.Credentials
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.datastore.DatastoreOptions
import gg.jte.TemplateEngine
import gg.jte.resolve.DirectoryCodeResolver
import gg.jte.resolve.ResourceCodeResolver
import io.ktor.server.application.*
import io.ktor.server.jte.*
import kotlin.io.path.Path

val datastore = DatastoreOptions.newBuilder()
    .setProjectId("spacemaven")
    .setCredentials(GoogleCredentials.getApplicationDefault())
    .build().service!!

fun main(args: Array<String>) {
    io.ktor.server.jetty.jakarta.EngineMain.main(args)
}

fun Application.module() {
    install(Jte) {
        if(this@module.developmentMode) {
            val resolver = ResourceCodeResolver("templates", ClassLoader.getSystemClassLoader())
            templateEngine = TemplateEngine.create(resolver, gg.jte.ContentType.Html)
        } else {
            templateEngine = TemplateEngine.createPrecompiled(gg.jte.ContentType.Html)
        }
    }

    configureSecurity()
    configureRouting()
}