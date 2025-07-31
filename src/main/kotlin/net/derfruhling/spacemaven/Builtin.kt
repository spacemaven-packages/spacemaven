package net.derfruhling.spacemaven

import com.google.cloud.datastore.Datastore
import com.google.cloud.datastore.Key
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import net.derfruhling.spacemaven.modules.headRef
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object Builtin : KoinComponent {
    @JvmStatic
    fun defaultConfigurationFor(repo: String): String? {
        return when(repo) {
            "gradle-plugins" -> null
            "tools" -> "tool"
            else -> "implementation"
        }
    }

    @JvmStatic
    fun repositoryUrlFor(repo: String): String = "https://spacemaven.derfruhling.net/$repo/"

    @JvmStatic
    fun getLatestVersionOf(name: String): String {
        return getLatestVersionOfOrNull(name) ?: "<???>"
    }

    @JvmStatic
    fun getLatestVersionOfOrNull(name: String): String? {
        val datastore by inject<Datastore>()
        val entity = datastore.get(Key.newBuilder("spacemaven", "HeadRef", name)
            .build())

        return entity?.let { headRef(it) }?.latestReleaseVersion
    }

    @get:JvmStatic
    var openTelemetry: OpenTelemetrySdk? = AutoConfiguredOpenTelemetrySdk.builder()
        .setResultAsGlobal()
        .build()
        .openTelemetrySdk
}