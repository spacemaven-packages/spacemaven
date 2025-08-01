package net.derfruhling.spacemaven

import com.google.cloud.datastore.Datastore
import com.google.cloud.datastore.Query
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.derfruhling.spacemaven.modules.get
import net.derfruhling.spacemaven.modules.specRef
import org.koin.ktor.ext.inject
import kotlin.time.Duration.Companion.days

fun Routing.setupSpecApi() {
    get("/spec") {
        val log = KotlinLogging.logger {}
        log.info { "Hello world" }

        val page: Int = call.request.queryParameters["page"]?.toInt() ?: 0

        val specRef = withContext(Dispatchers.Unconfined) {
            buildList {
                val datastore by inject<Datastore>()
                val results = datastore.run(
                    Query.newEntityQueryBuilder()
                        .setLimit(20)
                        .setOffset(page * 20)
                        .setKind("SpecRef")
                        .build()
                )
                for (it in results) {
                    add(specRef(it))
                }
            }
        }

        call.response.cacheControl(
            CacheControl.MaxAge(
                1.days.inWholeSeconds.toInt(),
                visibility = CacheControl.Visibility.Public
            )
        )

        call.respond(specRef)
    }

    get("/spec/{fullSpec}") {
        val fullSpec: String by call.parameters

        val specRef = withContext(Dispatchers.Unconfined) {
            val datastore by inject<Datastore>()
            val key = datastore.newKeyFactory()
                .setKind("SpecRef")
                .newKey(fullSpec)

            datastore.get(key)?.let {
                specRef(it)
            }
        }

        when (specRef) {
            null -> call.respond(HttpStatusCode.NotFound)
            else -> call.respond(specRef)
        }
    }
}