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
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import net.derfruhling.spacemaven.HeadRef
import net.derfruhling.spacemaven.SpecRef
import net.derfruhling.spacemaven.setupLogging
import net.derfruhling.spacemaven.setupSpecApi
import net.derfruhling.spacemaven.setupWebApp
import nl.adaptivity.xmlutil.dom.iterator
import org.koin.ktor.ext.inject
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.EOFException
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.name
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
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
    it.getString("release"),
    it.getStringOrNull("description"),
    it.getListOrNull("developers")?.parseDeveloperList(),
    it.getListOrNull("contributors")?.parseDeveloperList(),
    it.getEntityOrNull("organization")?.parseOrganization(),
    it.getEntityOrNull("scm")?.parseScm()
)

fun List<EntityValue>.parseDeveloperList() = map {
    val entity = it.get()!!
    SpecRef.Developer(
        entity.getString("name"),
        entity.getStringOrNull("url"),
        entity.getStringOrNull("email"),
        entity.getStringOrNull("timezone"),
        entity.getStringOrNull("organization"),
        entity.getStringOrNull("organizationUrl"),
        entity.getStringOrNull("pfpUrl"),
    )
}

fun FullEntity<*>.parseOrganization() = SpecRef.Organization(
    getString("name")!!,
    getString("url")!!
)

fun FullEntity<*>.parseScm() = SpecRef.SourceCodeRef(
    getString("tag")!!,
    getString("url")!!,
    getString("connection")!!,
    getString("developerConnection")!!,
)

private fun FullEntity<*>.getStringOrNull(name: String): String? = try {
    getString(name)
} catch (_: DatastoreException) {
    null
}

private fun Entity.getListOrNull(name: String): List<EntityValue>? = try {
    getList(name)
} catch (_: DatastoreException) {
    null
}

private fun Entity.getEntityOrNull(name: String): FullEntity<IncompleteKey>? = try {
    getEntity(name)
} catch (_: DatastoreException) {
    null
}

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

fun Application.getGradlePluginHeadRefs(page: Int, repository: String): List<HeadRef> {
    val datastore by inject<Datastore>()

    val results = datastore.run(
        Query.newEntityQueryBuilder()
            .setLimit(20)
            .setOffset(page * 20)
            .setKind("HeadRef")
            .setNamespace(repository)
            .setFilter(eq("isGradlePlugin", true))
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
    staticFiles(path, dir) {
        contentType {
            when(it.extension) {
                "pom" -> ContentType.Text.Xml
                "sha1", "sha256", "sha512", "md5" -> ContentType.Text.Plain
                else -> null
            }
        }

        cacheControl {
            when(it.extension) {
                "pom", "sha1", "sha256", "sha512", "md5", "asc" -> listOf(CacheControl.MaxAge(maxAgeSeconds = 1.hours.inWholeSeconds.toInt(), mustRevalidate = true, visibility = CacheControl.Visibility.Public))
                else -> listOf(CacheControl.MaxAge(maxAgeSeconds = 7.days.inWholeSeconds.toInt(), visibility = CacheControl.Visibility.Public))
            }
        }

        enableAutoHeadResponse()
    }

    val repoName = path.removeSurrounding("/")

    route("$path{...}") {
        install(validatePath)

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

            Files.newByteChannel(newPath,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE).use { call.receiveChannel().copyTo(it) }

            if (isMavenRepository && !newFile.parent.contains("_")) {
                if (newFile.name == "maven-metadata.xml") {
                    readMetadataDocument(dir, repoName, newFile)
                }

                if (newFile.extension == "pom") {
                    readPomDocument(dir, repoName, newFile)
                }
            }

            if(!isMavenRepository) {
                // build cache access times
                val key = Key.newBuilder("spacemaven", "TrackedBuildCacheAccessTime", newPath.name)
                    .setNamespace(path.substringBeforeLast('/').replace('/', '.'))
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
    val artifactId = document.getElementsByTagName("artifactId").item(0)?.textContent ?: return@withContext
    val versioning = document.getElementsByTagName("versioning").item(0) as Element? ?: return@withContext
    val latest = document.getElementsByTagName("latest").item(0)?.textContent
    val release = document.getElementsByTagName("release").item(0)?.textContent
    val versions = versioning.getElementsByTagName("versions").item(0) as Element? ?: return@withContext

    val versionNodes = versions.childNodes
    var updated = 0
    val tx = datastore.newTransaction()

    try {
        for(i in 0..<versionNodes.length) {
            val e = (versionNodes.item(i) ?: continue) as? Element ?: continue
            if(e.tagName != "version") continue

            val version = e.textContent ?: continue
            val fullSpecifier = "$groupId:$artifactId:$version";

            updated++
            log.debug { "Cataloging artifact $fullSpecifier" }

            log.debug { "Writing head ref: $fullSpecifier, latest = $latest, release = $release" }

            val headRefKey = datastore.newKeyFactory()
                .setKind("HeadRef")
                .setNamespace(repoName)
                .newKey("$groupId:$artifactId")

            val headBuilder =
                (datastore.get(headRefKey)?.let { Entity.newBuilder(headRefKey, it) } ?: Entity.newBuilder(headRefKey))
                    .set("groupId", groupId)
                    .set("artifactId", artifactId)
                    .set("repository", repoName)
                    .set("isGradlePlugin", artifactId == "$groupId.gradle.plugin")

            if(latest != null) headBuilder.set("latest", StringValue.newBuilder(latest).setExcludeFromIndexes(true).build())
            else headBuilder.setNull("latest")

            if(release != null) headBuilder.set("release", StringValue.newBuilder(release).setExcludeFromIndexes(true).build())
            else headBuilder.setNull("release")

            tx.put(headBuilder.build())

            log.debug { "Writing spec ref: $fullSpecifier, latest = $latest, release = $release" }

            val specRefKey = Key.newBuilder("spacemaven", "SpecRef", fullSpecifier)
                .setKind("SpecRef")
                .setNamespace(repoName)
                .addAncestor(PathElement.of(headRefKey.kind, headRefKey.name))
                .build()

            val specBuilder =
                (datastore.get(specRefKey)?.let { Entity.newBuilder(specRefKey, it) } ?: Entity.newBuilder(specRefKey))
                    .set("groupId", groupId)
                    .set("artifactId", artifactId)
                    .set("version", version)
                    .set("repository", repoName)
                    .set("isGradlePlugin", artifactId == "$groupId.gradle.plugin")

            if(latest != null) specBuilder.set("latest", StringValue.newBuilder(latest).setExcludeFromIndexes(true).build())
            else specBuilder.setNull("latest")

            if(release != null) specBuilder.set("release", StringValue.newBuilder(release).setExcludeFromIndexes(true).build())
            else specBuilder.setNull("release")

            tx.put(specBuilder.build())
        }

        tx.commit()
    } catch (e: Exception) {
        tx.rollback()
        log.error(e) { "Failed to catalog $groupId:$artifactId" }
        return@withContext
    }

    log.info { "Observed ${versionNodes.length}, updated $updated versions of $groupId:$artifactId" }
}

private suspend fun Route.readPomDocument(dir: File, repoName: String, newFile: File) = withContext(Dispatchers.Unconfined) {
    val datastore by inject<Datastore>()

    val log by lazy { KotlinLogging.logger {} }
    val document = newFile.inputStream().buffered().use { documentBuilders.newDocumentBuilder().parse(it) }

    val groupId = document.getElementsByTagName("groupId").item(0)?.textContent ?: return@withContext
    val artifactId = document.getElementsByTagName("artifactId").item(0)?.textContent ?: return@withContext
    val version = document.getElementsByTagName("version").item(0)?.textContent ?: return@withContext
    val fullSpecifier = "$groupId:$artifactId:$version"

    val tx = datastore.newTransaction()

    try {
        for((kind, keyName, ancestor) in arrayOf(
            Triple("SpecRef", fullSpecifier, PathElement.of("HeadRef", "$groupId:$artifactId")),
            Triple("HeadRef", "$groupId:$artifactId", null)
        )) {
            val keyBuilder = datastore.newKeyFactory()
                .setKind(kind)
                .setNamespace(repoName)

            ancestor?.let { keyBuilder.addAncestor(it) }
            val key = keyBuilder.newKey(keyName)

            val original = datastore.get(key)
            val entity = createSpecRefInfoFromPomDocument(original, key, document).build()

            if(entity.properties.isEmpty()) {
                log.info { "No catalogable properties available for package $fullSpecifier" }
                return@withContext
            }

            tx.put(entity)

            log.debug { "Writing info for package $fullSpecifier on kind $kind" }
        }

        tx.commit()

        log.info { "Cataloged info for package $fullSpecifier" }
    } catch(e: Exception) {
        log.error(e) { "Failed to write info for refs due to an exception" }
        tx.rollback()
    }
}

private fun createSpecRefInfoFromPomDocument(original: Entity?, parent: Key, document: Document): Entity.Builder {
    val builder = original?.let { Entity.newBuilder(parent, it) } ?: Entity.newBuilder(parent)

    document.getElementsByTagName("description").item(0)?.textContent?.let {
        builder.set("description", it)
    }

    val developers = ListValue.newBuilder()
    val contributors = ListValue.newBuilder()

    for (node in document.getElementsByTagName("developer").asIterable().filterIsInstance<Element>()) {
        developers.addValue(readMavenDeveloper(parent, node))
    }

    for (node in document.getElementsByTagName("contributor").asIterable().filterIsInstance<Element>()) {
        contributors.addValue(readMavenDeveloper(parent, node))
    }

    if (developers.get().isNotEmpty()) builder.set("developers", developers.build())
    if (contributors.get().isNotEmpty()) builder.set("contributors", contributors.build())

    document.getElementsByTagName("organization").item(0)?.let {
        val element = it as Element
        val name = element.getElementsByTagName("name").item(0)?.textContent ?: return@let
        val url = element.getElementsByTagName("url").item(0)?.textContent ?: return@let

        builder.set(
            "organization",
            EntityValue.newBuilder(
                Entity.newBuilder(Key.newBuilder(parent, "Organization").build())
                    .set("name", name)
                    .set("url", url)
                    .build()
            )
                .setExcludeFromIndexes(true)
                .build()
        )
    }

    document.getElementsByTagName("scm").item(0)?.let {
        val element = it as Element
        val connection = element.getElementsByTagName("connection").item(0)?.textContent ?: return@let
        val developerConnection = element.getElementsByTagName("developerConnection").item(0)?.textContent ?: return@let
        val tag = element.getElementsByTagName("tag").item(0)?.textContent ?: return@let
        val url = element.getElementsByTagName("url").item(0)?.textContent ?: return@let

        builder.set(
            "scm",
            EntityValue.newBuilder(
                Entity.newBuilder(Key.newBuilder(parent, "SourceControlInfo").build())
                    .set("connection", connection)
                    .set("developerConnection", developerConnection)
                    .set("tag", tag)
                    .set("url", url)
                    .build()
            )
                .setExcludeFromIndexes(true)
                .build()
        )
    }

    return builder
}

private fun readMavenDeveloper(parent: Key, node: Element): FullEntity<IncompleteKey>? {
    val name = node.getElementsByTagName("name").item(0)?.textContent ?: return null
    val url = node.getElementsByTagName("url").item(0)?.textContent
    val email = node.getElementsByTagName("email").item(0)?.textContent
    val timezone = node.getElementsByTagName("timezone").item(0)?.textContent
    val organization = node.getElementsByTagName("organization").item(0)?.textContent
    val organizationUrl = node.getElementsByTagName("organizationUrl").item(0)?.textContent
    val properties = node.getElementsByTagName("properties") as? Element?
    val pfpUrl = properties?.getElementsByTagName("picUrl")?.item(0)?.textContent

    val builder = Entity.newBuilder(Key.newBuilder(parent, "Developer").build())
        .set("name", name)

    url?.let { builder.set("url", it) }
    email?.let { builder.set("email", it) }
    timezone?.let { builder.set("timezone", it) }
    organization?.let { builder.set("organization", it) }
    organizationUrl?.let { builder.set("organizationUrl", it) }
    pfpUrl?.let { builder.set("pfpUrl", it) }

    return builder.build()
}

fun NodeList.asIterable() = Iterable { iterator() }
