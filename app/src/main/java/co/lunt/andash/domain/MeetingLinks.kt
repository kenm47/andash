package co.lunt.andash.domain

import co.lunt.andash.model.MeetingLink

/** Finds an online-meeting URL in free text (location and description fields). */
object MeetingLinks {
    private val patterns = listOf(
        "Meet" to Regex("""https?://meet\.google\.com/[A-Za-z0-9\-_?=&%.]+""", RegexOption.IGNORE_CASE),
        "Zoom" to Regex("""https?://[A-Za-z0-9.\-]*zoom\.us/[A-Za-z0-9/\-_?=&%.]+""", RegexOption.IGNORE_CASE),
        "Teams" to Regex("""https?://teams\.(microsoft|live)\.com/[A-Za-z0-9/\-_?=&%.:@+~]+""", RegexOption.IGNORE_CASE),
        "Webex" to Regex("""https?://[A-Za-z0-9.\-]*webex\.com/[A-Za-z0-9/\-_?=&%.]+""", RegexOption.IGNORE_CASE),
    )

    fun find(vararg texts: String?): MeetingLink? {
        val text = texts.filterNotNull().joinToString("\n")
        if (text.isBlank()) return null
        for ((kind, regex) in patterns) {
            val match = regex.find(text) ?: continue
            return MeetingLink(kind, match.value.trimEnd('.', ',', ')', '>'))
        }
        return null
    }
}
