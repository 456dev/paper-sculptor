package io.papermc.sculptor.version

import io.papermc.sculptor.shared.DOWNLOAD_SERVER_JAR
import io.papermc.sculptor.shared.MC_MANIFEST
import io.papermc.sculptor.shared.MC_VERSION
import io.papermc.sculptor.shared.SERVER_LIBRARIES_LIST
import io.papermc.sculptor.shared.SERVER_MAPPINGS
import io.papermc.sculptor.shared.data.LibrariesList
import io.papermc.sculptor.shared.data.api.MinecraftManifest
import io.papermc.sculptor.shared.data.api.MinecraftVersionManifest
import io.papermc.sculptor.shared.data.json
import io.papermc.sculptor.shared.util.*
import kotlin.io.path.exists
import kotlin.io.path.useLines
import kotlin.io.path.writeText
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.resources.TextResource
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

object ConfigureVersionProject {

    fun configure(target: Project, mache: MacheExtension) {
        return target.configure0(mache)
    }

    private fun Project.configure0(mache: MacheExtension) {
        val mcManifestFile: RegularFile = rootProject.layout.dotGradleDirectory.file(MC_MANIFEST)
        val mcManifest = json.decodeFromString<MinecraftManifest>(resources.text.fromFile(mcManifestFile).asString())

        val mcVersionManifestFile: RegularFile = layout.dotGradleDirectory.file(MC_VERSION)
        val mcVersion = mcManifest.versions.firstOrNull { it.id == mache.minecraftVersion.get() }
            ?: throw RuntimeException("Unknown Minecraft version ${mache.minecraftVersion.get()}")
        download.download(mcVersion.url, mcVersionManifestFile, Hash(mcVersion.sha1, HashingAlgorithm.SHA1))

        val manifestResource: TextResource = resources.text.fromFile(mcVersionManifestFile)
        val mcVersionManifest = json.decodeFromString<MinecraftVersionManifest>(manifestResource.asString())

        // must be configured now before the value of the property is read later
        configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(mcVersionManifest.javaVersion.majorVersion))
            }
        }
        tasks.withType(JavaCompile::class).configureEach {
            options.release.set(mcVersionManifest.javaVersion.majorVersion)
        }

        val downloadServerJarFile = layout.dotGradleDirectory.file(DOWNLOAD_SERVER_JAR)
        val serverMappingsFile = layout.dotGradleDirectory.file(SERVER_MAPPINGS)
        downloadServerFiles(download, mcVersionManifest, downloadServerJarFile, serverMappingsFile)

        val serverHash = downloadServerJarFile.convertToPath().hashFile(HashingAlgorithm.SHA256).asHexString()

        val librariesFile = layout.dotGradleDirectory.file(SERVER_LIBRARIES_LIST)
        val libraries = determineLibraries(downloadServerJarFile, serverHash, librariesFile)

        dependencies {
            for (library in libraries) {
                "minecraft"(library)
            }
        }
    }

    private fun Project.downloadServerFiles(
        download: DownloadService,
        manifest: MinecraftVersionManifest,
        serverJar: Any,
        serverMappings: Any,
    ) {
        runBlocking {
            awaitAll(
                download.downloadAsync(
                    manifest.downloads.server.url,
                    serverJar,
                    Hash(manifest.downloads.server.sha1, HashingAlgorithm.SHA1),
                ),
                download.downloadAsync(
                    manifest.downloads.serverMappings.url,
                    serverMappings,
                    Hash(manifest.downloads.serverMappings.sha1, HashingAlgorithm.SHA1),
                ) {
                    log("Downloading server jar")
                },
            )
        }
    }

    private fun Project.determineLibraries(jar: Any, serverHash: String, libraries: Any): List<String> {
        val librariesJson = libraries.convertToPath()
        val libs = if (librariesJson.exists()) {
            json.decodeFromString<LibrariesList>(resources.text.fromFile(libraries).asString())
        } else {
            null
        }

        val serverJar = jar.convertToPath()
        if (libs != null) {
            if (serverHash == libs.sha256) {
                return libs.libraries
            }
        }

        val result = serverJar.useZip { root ->
            val librariesList = root.resolve("META-INF").resolve("libraries.list")

            return@useZip librariesList.useLines { lines ->
                return@useLines lines.map { line ->
                    val parts = line.split(whitespace)
                    if (parts.size != 3) {
                        throw Exception("libraries.list file is invalid")
                    }
                    return@map parts[1]
                }.toList()
            }
        }

        val resultList = json.encodeToString(LibrariesList(serverHash, result))
        librariesJson.writeText(resultList)
        return result
    }

    private fun Project.log(msg: String) {
        println("$path > $msg")
    }
}
