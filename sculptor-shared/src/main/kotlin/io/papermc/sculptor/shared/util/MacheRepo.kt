package io.papermc.sculptor.shared.util

import org.gradle.api.Named
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

abstract class MacheRepo : Named {
    /**
     * The base Maven repository URL.
     */
    @get:Input
    abstract val url: Property<String>

    /**
     * Use this repository only for the given groups and their subgroups.
     */
    @get:Input
    abstract val includeGroups: ListProperty<String>

    @Input
    abstract override fun getName(): String
}

data class SimpleMacheRepo(
    val url: String,
    val name: String,
    val includeGroups: List<String>,
)
