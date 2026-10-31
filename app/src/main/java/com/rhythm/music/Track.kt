package com.rhythm.music

data class Track(
    val id: String,
    val name: String,
    val artist: String,
    val audioUrl: String,
    val imageUrl: String = "",
    val downloadUrl: String = "",
    val downloadAllowed: Boolean = false
)
