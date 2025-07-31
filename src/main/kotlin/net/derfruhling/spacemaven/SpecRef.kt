package net.derfruhling.spacemaven

import kotlinx.serialization.Serializable

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