package net.derfruhling.spacemaven.modules

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.datastore.Datastore
import com.google.cloud.datastore.DatastoreOptions
import io.ktor.server.application.*
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureDependencies() {
    install(Koin) {
        slf4jLogger()
        modules(module {
            single<Datastore>(createdAtStart = true) {
                DatastoreOptions.newBuilder()
                    .setProjectId("spacemaven")
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .build().service!!
            }
        })
    }
}
