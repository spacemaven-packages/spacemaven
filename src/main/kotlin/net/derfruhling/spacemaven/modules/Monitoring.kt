package net.derfruhling.spacemaven.modules

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.instrumentation.ktor.v3_0.KtorServerTelemetry
import net.derfruhling.spacemaven.Builtin
import net.derfruhling.spacemaven.loggingPlugin
import java.time.Instant

fun Application.configureMonitoring() {
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
}