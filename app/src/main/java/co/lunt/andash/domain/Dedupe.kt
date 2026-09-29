package co.lunt.andash.domain

import co.lunt.andash.model.Meeting
import java.util.Locale

/** Builds the cross-account identity of a meeting. */
fun meetingKey(uid: String?, title: String, startMillis: Long, endMillis: Long): String =
    if (!uid.isNullOrBlank()) {
        "uid:$uid@$startMillis"
    } else {
        "t:${title.trim().lowercase(Locale.ROOT)}@$startMillis-$endMillis"
    }

/**
 * Collapses meetings with the same [Meeting.key] (the same event synced through several
 * accounts) into one entry that remembers every account, keeping the first entry's order.
 */
fun dedupe(meetings: List<Meeting>): List<Meeting> {
    val merged = LinkedHashMap<String, Meeting>()
    for (m in meetings) {
        val existing = merged[m.key]
        merged[m.key] = if (existing == null) {
            m
        } else {
            existing.copy(
                accounts = (existing.accounts + m.accounts).distinct(),
                location = existing.location ?: m.location,
                link = existing.link ?: m.link,
            )
        }
    }
    return merged.values.toList()
}
