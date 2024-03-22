package io.papermc.sculptor.shared.data

import kotlinx.serialization.Serializable

@Serializable
data class LibrariesList(
    val sha256: String,
    val libraries: List<String>,
)
