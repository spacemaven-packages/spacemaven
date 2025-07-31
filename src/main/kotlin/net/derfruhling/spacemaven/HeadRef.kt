package net.derfruhling.spacemaven

import kotlinx.serialization.Serializable

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