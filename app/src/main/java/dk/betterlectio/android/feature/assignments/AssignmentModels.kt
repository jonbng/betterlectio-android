package dk.betterlectio.android.feature.assignments

import java.time.LocalDateTime

enum class AssignmentFilter {
    ALL,
    AWAITING_ME,
    DELIVERED,
    MISSING,
}

data class AssignmentItem(
    val id: String,
    val title: String,
    val team: String,
    val week: Int,
    val deadline: LocalDateTime?,
    val status: String,
    val studentTime: Double,
    val awaits: String,
    val note: String,
    val absence: String = "",
)

data class AssignmentDetail(
    val item: AssignmentItem,
    val description: String = "",
    val files: List<Pair<String, String>> = emptyList(), // name to url
    val responsible: String = "",
    val grading: String = "",
)

fun AssignmentItem.matches(filter: AssignmentFilter): Boolean {
    val s = status.lowercase()
    val a = awaits.lowercase()
    return when (filter) {
        AssignmentFilter.ALL -> true
        AssignmentFilter.AWAITING_ME ->
            a.contains("elev") || s.contains("afventer") || s.contains("mangler")
        AssignmentFilter.DELIVERED ->
            s.contains("aflever") || s.contains("afsluttet") || a.contains("lærer")
        AssignmentFilter.MISSING ->
            s.contains("mangl") || s.contains("ikke aflever")
    }
}
