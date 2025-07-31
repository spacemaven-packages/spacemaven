package net.derfruhling.spacemaven.modules

import com.google.cloud.datastore.*
import com.google.cloud.datastore.StructuredQuery.CompositeFilter.and
import com.google.cloud.datastore.StructuredQuery.PropertyFilter.eq
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.serialization.kotlinx.cbor.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.serialization.kotlinx.xml.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import net.derfruhling.spacemaven.HeadRef
import net.derfruhling.spacemaven.SpecRef
import net.derfruhling.spacemaven.setupLogging
import net.derfruhling.spacemaven.setupSpecApi
import net.derfruhling.spacemaven.setupWebApp
import org.koin.ktor.ext.inject
import org.w3c.dom.Element
import java.io.EOFException
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.name
import io.ktor.server.routing.get as getVerbatim
import io.ktor.server.routing.put as putVerbatim

val validatePath = createRouteScopedPlugin("validatePath") {
    onCall { call ->
        val path = call.request.path()
        if(path.contains("..")) call.respond(HttpStatusCode.Forbidden)
    }
}

@KtorDsl
inline fun Route.get(crossinline f: suspend RoutingContext.() -> Unit) = getVerbatim {
    setupLogging { f() }
}

@KtorDsl
inline fun Route.put(crossinline f: suspend RoutingContext.() -> Unit) = putVerbatim {
    setupLogging { f() }
}

@KtorDsl
inline fun Route.get(path: String, crossinline f: suspend RoutingContext.() -> Unit) = getVerbatim(path) {
    setupLogging { f() }
}

@KtorDsl
inline fun Route.put(path: String, crossinline f: suspend RoutingContext.() -> Unit) = putVerbatim(path) {
    setupLogging { f() }
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

        setupSpecApi()

        /*
         * Various repositories:
         * - public: Hosts Java/Kotlin libraries
         * - tools: Hosts Native and Java/Kotlin libraries:
         *          All packages are to match the following requirements to be consumable:
         *          - Contain an artifact with classifier 'tool' and extension 'zip'
         *          - This file must be a zip file containing directories 'bin' and 'lib'
         *          - All files in bin must either be binaries or scripts,
         *            which can refer to 'lib' by refering to it as '../lib' or '..\lib'
         *          - On supported platforms, 'lib' will also be added to the runtime DLL
         *            paths. On Unix, this means the environment variable LD_LIBRARY_PATH.
         * - gradle-plugins: Hosts exclusively gradle plugins.
         * - native: Hosts exclusively projects built with Gradle's builtin native
         *           functionality. Kotlin MP projects should use 'public' instead.
         */
        bucket(!developmentMode, "/public/", dir.resolve("public"))
        bucket(!developmentMode, "/tools/", dir.resolve("tools"))
        bucket(!developmentMode, "/gradle-plugins/", dir.resolve("gradle-plugins"))
        bucket(!developmentMode, "/native/", dir.resolve("native"))

        /*
         * A build cache is also hosted in a similar manner to repositories.
         */
        bucket(!developmentMode, "/build-cache/", dir.resolve("build-cache"), isMavenRepository = false)

        setupWebApp(this@configureRouting)
    }
}

private suspend fun Application.getAllSpecRefs(page: Int): List<SpecRef> {
    val datastore by inject<Datastore>()

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

suspend fun Application.getAllSpecRefs(repoName: String, page: Int, groupId: String, artifactId: String): List<SpecRef> {
    val datastore by inject<Datastore>()

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

fun specRef(it: Entity) = SpecRef(
    it.getString("groupId"),
    it.getString("artifactId"),
    it.getString("version"),
    it.getString("repository"),
    it.getString("latest"),
    it.getString("release")
)

fun Application.getAllHeadRefs(page: Int, repository: String): List<HeadRef> {
    val datastore by inject<Datastore>()

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

private fun Route.bucket(
    developmentMode: Boolean,
    path: String,
    dir: File,
    publishAuth: Boolean = true,
    isMavenRepository: Boolean = true
) {
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

        if(publishAuth) {
            authenticate("publish") {
                setupBucketPut(path, dir, repoName, isMavenRepository)
            }
        } else {
            setupBucketPut(path, dir, repoName, isMavenRepository)
        }
    }
}

private fun Route.setupBucketPut(path: String, dir: File, repoName: String, isMavenRepository: Boolean) {
    val datastore by inject<Datastore>()

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

            val newPath = newFile.toPath()
            Files.createDirectories(newPath.parent)

            val channel = call.receiveChannel()
            Files.newByteChannel(newPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
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

            if (isMavenRepository && newFile.name == "maven-metadata.xml" && !newFile.parent.matches(Regex("([^_]+)_(debug|release)_([^_]+)"))) {
                readMetadataDocument(dir, repoName, newFile)
            }

            if(!isMavenRepository) {
                // build cache access times
                val key = Key.newBuilder("spacemaven", "TrackedBuildCacheAccessTime", newPath.name)
                    .setNamespace(path.replace('/', '.'))
                    .build()

                datastore.put(Entity.newBuilder(key)
                    .set("lastAccessed", Instant.now().epochSecond)
                    .build())
            }

            call.respond(if (updating) HttpStatusCode.OK else HttpStatusCode.Created)
        } else {
            call.respond(HttpStatusCode.Forbidden)
        }
    }
}

private val documentBuilders = DocumentBuilderFactory.newInstance()

private suspend fun Route.readMetadataDocument(dir: File, repoName: String, newFile: File) = withContext(Dispatchers.Unconfined) {
    val datastore by inject<Datastore>()

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