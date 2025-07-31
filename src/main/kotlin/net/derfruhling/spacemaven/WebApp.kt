package net.derfruhling.spacemaven

import com.google.cloud.datastore.Datastore
import com.google.cloud.datastore.Key
import com.google.cloud.datastore.PathElement
import com.google.cloud.datastore.Query
import com.google.cloud.datastore.StructuredQuery
import com.google.cloud.datastore.aggregation.Aggregation
import com.google.common.collect.Iterables
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.jte.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import net.derfruhling.spacemaven.modules.get
import net.derfruhling.spacemaven.modules.getAllHeadRefs
import net.derfruhling.spacemaven.modules.getAllSpecRefs
import net.derfruhling.spacemaven.modules.specRef
import org.koin.ktor.ext.inject
import kotlin.time.Duration.Companion.hours

fun Routing.setupWebApp(application: Application) {
    route("/") {
        get {
            call.response.cacheControl(
                CacheControl.MaxAge(
                    24.hours.inWholeSeconds.toInt(),
                    visibility = CacheControl.Visibility.Public
                )
            )

            val specs = listOf(
                application.async { "native" to application.getAllHeadRefs(0, "native") },
                application.async { "tools" to application.getAllHeadRefs(0, "tools") },
                application.async { "public" to application.getAllHeadRefs(0, "public") },
                application.async { "gradle-plugins" to application.getAllHeadRefs(0, "gradle-plugins") },
            )

            call.respond(
                JteContent(
                    "index.jte", mapOf(
                        "locale" to getLocalizationContext(call.request.acceptLanguageItems().map { it.value }),
                        "allSpecs" to specs.awaitAll(),
                        /*
                    "page" to page,
                    "count" to datastore.runAggregation(Query.newAggregationQueryBuilder()
                        .over(Query.newEntityQueryBuilder()
                            .setKind("HeadRef")
                            .build())
                        .addAggregation(Aggregation.count().`as`("count"))
                        .build())
                        .let { Iterables.getOnlyElement(it).get("count") }*/
                    )
                )
            )
        }

        route("/repo/{repo}/{groupId}/{artifactId}") {
            get {
                call.response.cacheControl(
                    CacheControl.MaxAge(
                        24.hours.inWholeSeconds.toInt(),
                        visibility = CacheControl.Visibility.Public
                    )
                )

                val repo: String by call.parameters
                val groupId: String by call.parameters
                val artifactId: String by call.parameters

                val page = call.queryParameters["page"]?.toInt() ?: 1
                val specs = application.getAllSpecRefs(
                    repo,
                    page - 1,
                    groupId,
                    artifactId
                )

                if (specs.isEmpty()) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }

                val datastore by inject<Datastore>()

                call.respond(
                    JteContent(
                        "head.jte", mapOf(
                            "locale" to getLocalizationContext(call.request.acceptLanguageItems().map { it.value }),
                            "specs" to specs,
                            "page" to page,
                            "groupId" to groupId,
                            "artifactId" to artifactId,
                            "repoName" to repo,
                            "count" to datastore.runAggregation(
                                Query.newAggregationQueryBuilder()
                                    .over(
                                        Query.newEntityQueryBuilder()
                                            .setKind("SpecRef")
                                            .setNamespace(repo)
                                            .setFilter(
                                                StructuredQuery.CompositeFilter.and(
                                                    StructuredQuery.PropertyFilter.eq(
                                                        "groupId",
                                                        groupId
                                                    ), StructuredQuery.PropertyFilter.eq("artifactId", artifactId)
                                                )
                                            )
                                            .build()
                                    )
                                    .addAggregation(Aggregation.count().`as`("count"))
                                    .build()
                            )
                                .let { Iterables.getOnlyElement(it).get("count") }
                        )))
            }

            route("/{version}") {
                get {
                    call.response.cacheControl(
                        CacheControl.MaxAge(
                            24.hours.inWholeSeconds.toInt(),
                            visibility = CacheControl.Visibility.Public
                        )
                    )

                    val repo: String by call.parameters
                    val groupId: String by call.parameters
                    val artifactId: String by call.parameters
                    val version: String by call.parameters

                    val specKey = Key.newBuilder("spacemaven", "SpecRef", "$groupId:$artifactId:$version")
                        .addAncestor(PathElement.of("HeadRef", "$groupId:$artifactId"))
                        .setNamespace(repo)
                        .build()

                    val datastore by inject<Datastore>()

                    val spec = datastore.get(specKey)?.let {
                        specRef(it)
                    } ?: run {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }

                    call.respond(
                        JteContent(
                            "dep.jte", mapOf(
                                "locale" to getLocalizationContext(
                                    call.request.acceptLanguageItems().map { it.value }),
                                "spec" to spec,
                            )
                        )
                    )
                }
            }
        }
    }
}