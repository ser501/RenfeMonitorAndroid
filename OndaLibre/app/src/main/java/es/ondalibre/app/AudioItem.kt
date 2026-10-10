package es.ondalibre.app

data class AudioItem(
    val title: String,
    val subtitle: String = "",
    val url: String = "",
    val source: String = "",
    val imageUrl: String = "",
    val feedUrl: String = "",
    val downloadUrl: String = "",
    val canDownload: Boolean = false,
    val description: String = ""
)
