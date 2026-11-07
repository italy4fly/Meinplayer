package app.meinplayer

data class Channel(
    val name: String,
    val url: String,
    val logo: String?,
    val group: String,
)

object M3u {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")
    private const val NO_GROUP = "Ohne Gruppe"

    fun parse(text: String): List<Channel> {
        val out = ArrayList<Channel>()
        var name: String? = null
        var logo: String? = null
        var group = NO_GROUP

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF") -> {
                    val a = attr.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                    logo = a["tvg-logo"]?.ifBlank { null }
                    group = a["group-title"]?.ifBlank { null } ?: NO_GROUP
                    name = line.substringAfterLast(',').trim().ifBlank { a["tvg-name"] }
                }
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> {
                    val n = name
                    if (n != null) out += Channel(n, line, logo, group)
                    name = null
                    logo = null
                    group = NO_GROUP
                }
            }
        }
        return out
    }
}
