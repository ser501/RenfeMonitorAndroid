package com.ondalibre.app.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/** Lee las canciones guardadas en el móvil (MP3, FLAC, M4A, OGG…). */
object LocalMusic {
    fun load(context: Context): List<Track> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 30000"
        val out = mutableListOf<Track>()
        context.contentResolver.query(
            collection, projection, selection, null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val artist = c.getString(artistCol).orEmpty()
                out += Track(
                    id = "l$id",
                    title = c.getString(titleCol).orEmpty(),
                    artist = if (artist == "<unknown>") "Artista desconocido" else artist,
                    album = c.getString(albumCol).orEmpty(),
                    imageUrl = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"), c.getLong(albumIdCol)
                    ).toString(),
                    audioUrl = ContentUris.withAppendedId(collection, id).toString(),
                    durationSec = (c.getLong(durCol) / 1000).toInt(),
                    source = Source.LOCAL,
                )
            }
        }
        TrackCache.put(out)
        return out
    }
}
