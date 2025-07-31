package net.derfruhling.spacemaven

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.datastore.DatastoreOptions
import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.resolve.ResourceCodeResolver
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.jte.*
import io.ktor.server.cio.*
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.instrumentation.ktor.v3_0.KtorServerTelemetry
import java.time.Instant

val datastore = DatastoreOptions.newBuilder()
    .setProjectId("spacemaven")
    .setCredentials(GoogleCredentials.getApplicationDefault())
    .build().service!!

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    install(loggingPlugin)

    Builtin.openTelemetry?.let { openTelemetry ->
        install(KtorServerTelemetry) {
            setOpenTelemetry(openTelemetry)

            capturedRequestHeaders(HttpHeaders.UserAgent)
            capturedResponseHeaders(HttpHeaders.ContentType)

            spanStatusExtractor {
                if (error != null) {
                    spanStatusBuilder.setStatus(StatusCode.ERROR)
                }
            }

            spanKindExtractor {
                SpanKind.CLIENT
            }

            attributesExtractor {
                onStart {
                    attributes.put("start-time", System.currentTimeMillis())
                }

                onEnd {
                    attributes.put("end-time", Instant.now().toEpochMilli())
                }
            }
        }
    }

    install(Jte) {
        if(this@module.developmentMode) {
            val resolver = ResourceCodeResolver("templates", ClassLoader.getSystemClassLoader())
            templateEngine = TemplateEngine.create(resolver, ContentType.Html)
        } else {
            templateEngine = TemplateEngine.createPrecompiled(ContentType.Html)
        }
    }

    configureSecurity()
    configureRouting()
}