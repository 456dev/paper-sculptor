package io.papermc.sculptor.shared.data.meta

import kotlinx.serialization.Serializable

@Serializable
data class MacheRepository(
    val url: String,
    val name: String,
    val groups: List<String>? = null,
)
