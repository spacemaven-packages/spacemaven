package net.derfruhling.spacemaven

import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.FullEntity
import com.google.cloud.datastore.Key
import com.google.cloud.datastore.Query
import com.google.cloud.datastore.StringValue
import com.google.cloud.datastore.aggregation.Aggregation
import com.google.common.collect.Iterables
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.serialization.kotlinx.cbor.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.serialization.kotlinx.xml.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.jte.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.utils.io.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.w3c.dom.Element
import java.io.EOFException
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import com.google.cloud.datastore.StructuredQuery.CompositeFilter.and
import com.google.cloud.datastore.StructuredQuery.PropertyFilter.eq
import kotlinx.coroutines.*

val validatePath = createRouteScopedPlugin("validatePath") {
    onCall { call ->
        val path = call.request.path()
        if(path.contains("..")) call.respond(HttpStatusCode.Forbidden)
    }
}

fun Application.configureRouting() {
    val dir = File(System.getenv("DATA_PATH") ?: "data").absoluteFile

    @OptIn(ExperimentalSerializationApi::class)
    install(ContentNegotiation) {
        jsonIo()
        cbor()
        xml()
    }

    routing {
        staticResources("/static", "/static")

        get("/spec") {
            val page: Int = call.request.queryParameters["page"]?.toInt() ?: 0

            val specRef = withContext(Dispatchers.Unconfined) {
                buildList {
                    val results = datastore.run(Query.newEntityQueryBuilder()
                        .setLimit(20)
                        .setOffset(page * 20)
                        .setKind("SpecRef")
                        .build())
                    for(it in results) {
                        add(SpecRef(
                            it.getString("groupId"),
                            it.getString("artifactId"),
                            it.getString("version"),
                            it.getString("repository"),
                            it.getString("latest"),
                            it.getString("release")
                        ))
                    }
                }
            }

            call.response.cacheControl(CacheControl.MaxAge(1.days.inWholeSeconds.toInt(), visibility = CacheControl.Visibility.Public))

            call.respond(specRef)
        }

        get("/spec/{fullSpec}") {
            val fullSpec: String by call.parameters

            val specRef = withContext(Dispatchers.Unconfined) {
                val key = datastore.newKeyFactory()
                    .setKind("SpecRef")
                    .newKey(fullSpec)

                datastore.get(key)?.let {
                    SpecRef(
                        it.getString("groupId"),
                        it.getString("artifactId"),
                        it.getString("version"),
                        it.getString("repository"),
                        it.getString("latest"),
                        it.getString("release")
                    )
                }
            }

            when(specRef) {
                null -> call.respond(HttpStatusCode.NotFound)
                else -> call.respond(specRef)
            }
        }

        bucket(!developmentMode, "/public/", dir.resolve("public"))
        bucket(!developmentMode, "/tools/", dir.resolve("tools"))
        bucket(!developmentMode, "/gradle-plugins/", dir.resolve("gradle-plugins"))
        bucket(!developmentMode, "/native/", dir.resolve("native"))

        // webapp

        route("/") {
            get {
                call.response.cacheControl(
                    CacheControl.MaxAge(
                        24.hours.inWholeSeconds.toInt(),
                        visibility = CacheControl.Visibility.Public
                    )
                )

                val specs = listOf(
                    async { "native" to getAllHeadRefs(0, "native") },
                    async { "tools" to getAllHeadRefs(0, "tools") },
                    async { "public" to getAllHeadRefs(0, "public") },
                    async { "gradle-plugins" to getAllHeadRefs(0, "gradle-plugins") },
                )

                call.respond(
                    JteContent(
                        "index.kte", mapOf(
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
                    val specs = getAllSpecRefs(repo, page - 1, groupId, artifactId)

                    if (specs.isEmpty()) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }

                    call.respond(
                        JteContent(
                        "head.kte", mapOf(
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
                                        .setFilter(and(eq("groupId", groupId), eq("artifactId", artifactId)))
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

                        val specKey = Key
                            .newBuilder("spacemaven", "SpecRef", "$groupId:$artifactId:$version")
                            .setNamespace(repo)
                            .build()

                        val spec = datastore.get(specKey)?.let { specRef(it) } ?: run {
                            call.respond(HttpStatusCode.NotFound)
                            return@get
                        }

                        call.respond(
                            JteContent(
                                "dep.kte", mapOf(
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
}

private suspend fun getAllSpecRefs(page: Int): List<SpecRef> {
    return withContext(Dispatchers.Unconfined) {
        buildList {
            val results = datastore.run(
                Query.newEntityQueryBuilder()
                    .setLimit(20)
                    .setOffset(page * 20)
                    .setKind("SpecRef")
                    .build()
            )
            for (it in results) {
                add(
                    specRef(it)
                )
            }
        }
    }
}

private suspend fun getAllSpecRefs(repoName: String, page: Int, groupId: String, artifactId: String): List<SpecRef> {
    return withContext(Dispatchers.Unconfined) {
        buildList {
            val results = datastore.run(
                Query.newEntityQueryBuilder()
                    .setLimit(20)
                    .setOffset(page * 20)
                    .setKind("SpecRef")
                    .setNamespace(repoName)
                    .setFilter(and(eq("groupId", groupId), eq("artifactId", artifactId)))
                    .build()
            )
            for (it in results) {
                add(
                    specRef(it)
                )
            }
        }
    }
}

private fun specRef(it: Entity) = SpecRef(
    it.getString("groupId"),
    it.getString("artifactId"),
    it.getString("version"),
    it.getString("repository"),
    it.getString("latest"),
    it.getString("release")
)

private fun getAllHeadRefs(page: Int, repository: String): List<HeadRef> {
    val results = datastore.run(
        Query.newEntityQueryBuilder()
            .setLimit(20)
            .setOffset(page * 20)
            .setKind("HeadRef")
            .setNamespace(repository)
            .build()
    )

    return buildList {
        for (it in results) {
            add(
                headRef(it)
            )
        }
    }
}

fun headRef(it: Entity) = HeadRef(
    it.getString("groupId"),
    it.getString("artifactId"),
    it.getString("repository"),
    it.getString("latest"),
    it.getString("release")
)

@Serializable
data class SpecRef(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val repository: String,
    val latestVersion: String?,
    val latestReleaseVersion: String?
) {
    val fullyQualifiedName get() = "$groupId:$artifactId:$version"
    val repositoryUrl get() = "https://spacemaven.derfruhling.net/$repository/"
}

@Serializable
data class HeadRef(
    val groupId: String,
    val artifactId: String,
    val repository: String,
    val latestVersion: String?,
    val latestReleaseVersion: String?
) {
    val fullyQualifiedName get() = "$groupId:$artifactId"
    val repositoryUrl get() = "https://spacemaven.derfruhling.net/$repository/"
}

private fun Route.bucket(developmentMode: Boolean, path: String, dir: File) {
    if(developmentMode) {
        staticFiles(path, dir)
    }

    val repoName = path.removeSurrounding("/")

    route("$path{...}") {
        install(validatePath)

        if(!developmentMode) {
            get {
                call.respondRedirect(true) {
                    takeFrom("https://storage.googleapis.com/repository-data${call.request.path()}")
                }
            }
        }

        authenticate("publish") {
            put {
                if (call.response.isCommitted) return@put
                val principal =
                    call.principal<PublishPrincipal>("publish")
                        ?: return@put call.respond(HttpStatusCode.Forbidden)

                val path = call.request.path().removePrefix(path)
                val fullPath = call.request.path()

                if (!principal.authority.any { fullPath.startsWith(it) }) return@put call.respond(HttpStatusCode.Forbidden);

                val newFile = dir.resolve(path).absoluteFile

                if (newFile.startsWith(dir)) {
                    val updating = newFile.exists()

                    Files.createDirectories(newFile.toPath().parent)

                    val channel = call.receiveChannel()
                    Files.newByteChannel(newFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
                        .use { sink ->
                            while (true) {
                                try {
                                    channel.read { buffer ->
                                        sink.write(buffer)
                                    }
                                } catch (_: EOFException) {
                                    break
                                }
                            }
                        }

                    if(newFile.name == "maven-metadata.xml" && !newFile.parent.matches(Regex("([^_]+)_(debug|release)_([^_]+)"))) {
                        readMetadataDocument(dir, repoName, newFile)
                    }

                    call.respond(if (updating) HttpStatusCode.OK else HttpStatusCode.Created)
                } else {
                    call.respond(HttpStatusCode.Forbidden)
                }
            }
        }
    }
}

private val documentBuilders = DocumentBuilderFactory.newInstance()

private suspend fun readMetadataDocument(dir: File, repoName: String, newFile: File) = withContext(Dispatchers.Unconfined) {
    val log by lazy { KotlinLogging.logger {} }
    val document = newFile.inputStream().buffered().use { documentBuilders.newDocumentBuilder().parse(it) }

    val groupId = document.getElementsByTagName("groupId").item(0)?.textContent ?: return@withContext
    val artifactId = document.getElementsByTagName("groupId").item(0)?.textContent ?: return@withContext
    val versioning = document.getElementsByTagName("versioning").item(0) as Element? ?: return@withContext
    val latest = document.getElementsByTagName("latest").item(0)?.textContent
    val release = document.getElementsByTagName("release").item(0)?.textContent
    val versions = versioning.getElementsByTagName("versions").item(0) as Element? ?: return@withContext

    val versionNodes = versions.childNodes
    var updated = 0
    for(i in 0..<versionNodes.length) {
        val e = (versionNodes.item(i) ?: continue) as? Element ?: continue
        if(e.tagName != "version") continue

        val version = e.textContent ?: continue
        val fullSpecifier = "$groupId:$artifactId:$version";

        updated++
        log.debug { "Cataloging artifact $fullSpecifier" }

        launch {
            log.debug { "Writing spec ref: $fullSpecifier, latest = $latest, release = $release" }

            val key = datastore.newKeyFactory()
                .setKind("SpecRef")
                .setNamespace(repoName)
                .newKey(fullSpecifier)

            datastore.put(FullEntity.newBuilder(key)
                .set("groupId", groupId)
                .set("artifactId", artifactId)
                .set("version", version)
                .set("repository", repoName)
                .set("latest", StringValue.newBuilder(latest).setExcludeFromIndexes(true).build())
                .set("release", StringValue.newBuilder(release).setExcludeFromIndexes(true).build())
                .build())
        }

        launch {
            log.debug { "Writing head ref: $fullSpecifier, latest = $latest, release = $release" }

            val partialKey = datastore.newKeyFactory()
                .setKind("HeadRef")
                .setNamespace(repoName)
                .newKey("$groupId:$artifactId")

            datastore.put(FullEntity.newBuilder(partialKey)
                .set("groupId", groupId)
                .set("artifactId", artifactId)
                .set("repository", repoName)
                .set("latest", StringValue.newBuilder(latest).setExcludeFromIndexes(true).build())
                .set("release", StringValue.newBuilder(release).setExcludeFromIndexes(true).build())
                .build())
        }
    }

    log.info { "Observed ${versionNodes.length}, updated $updated versions of $groupId:$artifactId" }
}