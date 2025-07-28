package net.derfruhling.spacemaven

import com.google.cloud.datastore.FullEntity
import com.google.cloud.datastore.Query
import com.google.cloud.datastore.StringValue
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.w3c.dom.Element
import java.io.EOFException
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.time.Duration.Companion.days

val validatePath = createRouteScopedPlugin("validatePath") {
    onCall { call ->
        val path = call.request.path()
        if(path.contains("..")) call.respond(HttpStatusCode.Forbidden)
    }
}

fun Application.configureRouting() {
    val dir = File(System.getenv("DATA_PATH") ?: "data").absoluteFile

    install(ContentNegotiation) {
        json()
    }

    routing {
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
                call.respond(JteContent("index.kte", mapOf(
                    "locale" to getLocalizationContext(call.request.acceptLanguageItems().map { it.value })
                )))
            }
        }
    }
}

@Serializable
data class SpecRef(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val repository: String,
    val latestVersion: String?,
    val latestReleaseVersion: String?
)

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
    for(i in 0..<versionNodes.length) {
        val e = (versionNodes.item(i) ?: continue) as? Element ?: continue
        if(e.tagName != "version") continue

        val version = e.textContent ?: continue
        val fullSpecifier = "$groupId:$artifactId:$version";

        launch {
            log.info { "Writing spec ref: $fullSpecifier, latest = $latest, release = $release" }

            val key = datastore.newKeyFactory()
                .setKind("SpecRef")
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
    }
}