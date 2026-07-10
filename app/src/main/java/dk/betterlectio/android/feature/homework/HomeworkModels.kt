package dk.betterlectio.android.feature.homework

import java.time.LocalDate

data class HomeworkItem(
    val id: String,
    val note: String,
    val activityTitle: String,
    val date: LocalDate?,
    val team: String = "",
    val done: Boolean = false,
    val href: String? = null,
    val detailHtml: String? = null,
)

data class HomeworkDayGroup(
    val date: LocalDate?,
    val label: String,
    val items: List<HomeworkItem>,
)

fun List<HomeworkItem>.groupedByDate(): List<HomeworkDayGroup> {
    val groups = groupBy { it.date }.toSortedMap(compareBy(nullsLast()) { it })
    return groups.map { (date, items) ->
        HomeworkDayGroup(
            date = date,
            label = date?.toString() ?: "Uden dato",
            items = items.sortedBy { it.activityTitle },
        )
    }
}
