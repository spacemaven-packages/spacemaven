package net.derfruhling.spacemaven

import kotlinx.serialization.Serializable

@Serializable
data class SpecRef(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val repository: String,
    val latestVersion: String?,
    val latestReleaseVersion: String?,
    val description: String?,
    val developers: List<Developer>?,
    val contributors: List<Developer>?,
    val organization: Organization?,
    val scm: SourceCodeRef?
) {
    val fullyQualifiedName get() = "$groupId:$artifactId:$version"
    val repositoryUrl get() = "https://spacemaven.derfruhling.net/$repository/"

    @Serializable
    data class Developer(
        val name: String,
        val url: String?,
        val email: String?,
        val timezone: String?,
        val organization: String?,
        val organizationUrl: String?,
        val pfpUrl: String?
    )

    @Serializable
    data class Organization(
        val name: String,
        val url: String
    )

    @Serializable
    data class SourceCodeRef(
        val tag: String?,
        val url: String?,
        val connection: String?,
        val developerConnection: String?
    )
}