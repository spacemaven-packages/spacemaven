package net.derfruhling.spacemaven

import com.google.cloud.datastore.Key
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk

object Builtin {
    fun defaultConfigurationFor(repo: String): String? {
        return when(repo) {
            "gradle-plugins" -> null
            "tools" -> "tool"
            else -> "implementation"
        }
    }

    fun repositoryUrlFor(repo: String): String = "https://spacemaven.derfruhling.net/$repo/"

    fun getLatestVersionOf(name: String): String? {
        val entity = datastore.get(Key.newBuilder("spacemaven", "HeadRef", name)
            .build())

        return entity?.let { headRef(it) }?.latestReleaseVersion
    }

    var openTelemetry: OpenTelemetrySdk? = AutoConfiguredOpenTelemetrySdk.builder()
        .setResultAsGlobal()
        .build()
        .openTelemetrySdk
}