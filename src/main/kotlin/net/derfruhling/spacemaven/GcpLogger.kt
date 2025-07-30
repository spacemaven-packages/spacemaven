package net.derfruhling.spacemaven

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.fasterxml.jackson.core.JsonGenerator
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.context.propagation.TextMapPropagator
import net.logstash.logback.composite.AbstractJsonProvider
import org.slf4j.MDC
import java.time.Instant
import kotlin.coroutines.*
import kotlin.reflect.KProperty



class GcpLogger(private val call: ApplicationCall) : ContinuationInterceptor {
    inner class Mdc<T>(val name: String, var value: T, val v: (T) -> String? = Any?::toString) {
        operator fun getValue(self: Any?, prop: KProperty<*>): T {
            return value
        }

        operator fun setValue(self: Any?, prop: KProperty<*>, value: T) {
            if(current.get() == this@GcpLogger) {
                when (val new = v(value)) {
                    null -> MDC.remove(name)
                    else -> push(name, new)
                }
            }
        }
    }

    private val mapped = ThreadLocal.withInitial { mutableListOf<String>() }

    @OptIn(ExperimentalStdlibApi::class)
    private val traceParts by lazy {
        (call.request.header("traceparent")
            ?.split('-')
            ?.subList(1, 3)
        ?: call.request.header("x-cloud-trace-context")
            ?.split(';', limit = 2)
            ?.first()
            ?.split('/', limit = 2)
            ?.let { listOf(it[0], it[1].toULong().toHexString()) })
    }

    val traceValue by lazy { traceParts?.get(0)?.let { "projects/spacemaven/traces/$it" } }
    private val otelSpanId by lazy { Span.current().spanContext.spanId.takeUnless { it.length == 16 && it.all { c -> c == '0' } } }
    val spanValue by lazy { otelSpanId ?: traceParts?.get(1) }

    val methodValue by lazy { call.request.httpMethod.value.uppercase() }
    val requestUrlValue by lazy { call.request.uri }
    val userAgentValue by lazy { call.request.userAgent() }
    val remoteIpValue by lazy { call.request.origin.run { "$remoteAddress:$remotePort" } }
    val referrerValue by lazy { call.request.header("Referer") }
    val protocolValue by lazy { call.request.httpVersion }

    var requestSizeValue: Int? by Mdc("gcpHttpRequestSize", null) { it?.toString() }
        private set
    var responseStatusValue: Int? by Mdc("gcpHttpResponseStatus", null) { it?.toString() }
        private set
    var responseSizeValue: Int? by Mdc("gcpHttpResponseSize", null) { it?.toString() }
        private set

    var currentOperationId: String? by Mdc("gcpOperationId", null)
        private set
    var currentOperationProducer: String? by Mdc("gcpOperationProducer", null)
        private set

    companion object {
        val current = ThreadLocal.withInitial<GcpLogger?> { null }
    }

    override val key: CoroutineContext.Key<*> = ContinuationInterceptor

    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
        return Continuation(continuation.context) { result ->
            setupContextFor { continuation.resumeWith(result) }
        }
    }

    internal fun bytesReceived(byteCount: Int) {
        requestSizeValue = (requestSizeValue ?: 0) + byteCount
    }

    private fun push(name: String, value: String?) {
        if(value == null) return
        mapped.get().add(name)
        MDC.put(name, value)
    }

    fun operation(id: String, producer: String, f: () -> Unit) {
        currentOperationId = id
        currentOperationProducer = producer

        try {
            f()
        } finally {
            currentOperationId = null
            currentOperationProducer = null
        }
    }

    private fun setupContextFor(f: () -> Unit) {
        push("gcpTrace", traceValue)
        push("gcpSpan", spanValue)
        push("gcpHttpMethod", methodValue)
        push("gcpHttpUrl", requestUrlValue)
        push("gcpHttpRemoteIp", remoteIpValue)
        push("gcpHttpUserAgent", userAgentValue)
        push("gcpHttpReferer", referrerValue)
        push("gcpHttpProtocol", protocolValue)

        push("gcpHttpRequestSize", requestSizeValue?.toString())
        push("gcpHttpResponseStatus", responseStatusValue?.toString())
        push("gcpHttpResponseSize", responseSizeValue?.toString())

        push("gcpOperationId", currentOperationId)
        push("gcpOperationProducer", currentOperationProducer)

        current.set(this)

        try {
            f()
        } finally {
            current.remove()

            MDC.clear()
            mapped.get().clear()
        }
    }

    suspend fun runSuspend(f: suspend () -> Unit) {
        val context = coroutineContext + this
        setupContextFor {
            f.createCoroutine(Continuation(context) { result ->
                result.exceptionOrNull()?.let { throw it }
            }).resume(Unit)
        }
    }

    class TraceJsonProvider : AbstractJsonProvider<ILoggingEvent>() {
        override fun writeTo(generator: JsonGenerator, event: ILoggingEvent) {
            generator.writeStringField("severity", when(event.level) {
                Level.ERROR -> "ERROR"
                Level.WARN -> "WARNING"
                Level.INFO -> "INFO"
                Level.DEBUG, Level.TRACE -> "DEBUG"
                else -> "DEFAULT"
            })

            val now = Instant.now()
            generator.writeStringField("time", now.toString())

            val map = event.mdcPropertyMap

            if("gcpTrace" in map) generator.writeStringField("logging.googleapis.com/trace", map["gcpTrace"]!!)
            if("gcpSpan" in map) generator.writeStringField("logging.googleapis.com/spanId", map["gcpSpan"]!!)
            generator.writeBooleanField("logging.googleapis.com/trace_sampled", "gcpTrace" in map && "gcpSpan" in map)

            if("gcpHttpMethod" in map) {
                generator.writeObjectFieldStart("httpRequest")

                val httpMethod = map["gcpHttpMethod"]!!
                val httpUri = map["gcpHttpUrl"]!!
                val httpRemoteIp = map["gcpHttpRemoteIp"]!!
                val protocol = map["gcpHttpProtocol"]!!
                val userAgent = map["gcpHttpUserAgent"]
                val referer = map["gcpHttpReferer"]
                val requestSize = map["gcpHttpRequestSize"]

                generator.writeStringField("requestMethod", httpMethod)
                generator.writeStringField("requestUrl", httpUri)
                generator.writeStringField("remoteIp", httpRemoteIp)
                generator.writeStringField("protocol", protocol)
                userAgent?.let { generator.writeStringField("userAgent", it) }
                referer?.let { generator.writeStringField("referer", it) }
                requestSize?.let { generator.writeStringField("requestSize", it) }

                generator.writeEndObject()
            }
        }
    }
}

suspend inline fun RoutingContext.setupLogging(crossinline f: suspend GcpLogger.() -> Unit) {
    val gcpLogger = GcpLogger(call)
    gcpLogger.runSuspend { gcpLogger.f() }
}

val loggingPlugin = createApplicationPlugin("loggingPlugin") {
    on(ReceiveRequestBytes) { _, body ->
        val logger = GcpLogger.current.get()

        object : ByteReadChannel by body {
            override suspend fun awaitContent(min: Int): Boolean {
                val value = body.awaitContent(min)
                logger.bytesReceived(this.availableForRead)
                return value
            }
        }
    }
}
