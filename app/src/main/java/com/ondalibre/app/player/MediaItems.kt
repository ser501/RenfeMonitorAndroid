package com.ondalibre.app.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.ondalibre.app.data.Source
import com.ondalibre.app.data.Track

fun Track.toMediaItem(): MediaItem {
    val uri = Uri.parse(audioUrl)
    val extras = Bundle().apply { putString("source", source.name) }
    val meta = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(imageUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
        .setExtras(extras)
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .build()
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        // La URI viaja también aquí porque el controlador no siempre la transmite a la sesión.
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(meta)
        .build()
}

fun MediaItem.isLocal(): Boolean =
    mediaMetadata.extras?.getString("source") == Source.LOCAL.name
