package com.midia.organizador

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

enum class Repeat(val label: String) {
    NONE("No se repite"),
    DAILY("Todos los días"),
    WEEKDAYS("De lunes a viernes"),
    WEEKLY("Cada semana (mismo día)")
}

data class Task(
    val id: Long,
    var title: String,
    var date: LocalDate,
    var hour: Int = -1,
    var minute: Int = 0,
    var repeat: Repeat = Repeat.NONE,
    val doneDates: MutableSet<String> = mutableSetOf(),
    var snoozeUntil: Long = 0L,
    var snoozeDate: String = ""
) {
    val hasTime: Boolean get() = hour >= 0

    fun occursOn(day: LocalDate): Boolean = when (repeat) {
        Repeat.NONE -> day == date
        Repeat.DAILY -> !day.isBefore(date)
        Repeat.WEEKDAYS -> !day.isBefore(date) &&
            day.dayOfWeek != DayOfWeek.SATURDAY && day.dayOfWeek != DayOfWeek.SUNDAY
        Repeat.WEEKLY -> !day.isBefore(date) && day.dayOfWeek == date.dayOfWeek
    }

    fun isDoneOn(day: LocalDate): Boolean = doneDates.contains(day.toString())

    fun timeText(): String =
        if (hasTime) String.format(Locale.ROOT, "%02d:%02d", hour, minute) else ""

    fun repeatText(): String = when (repeat) {
        Repeat.NONE -> ""
        Repeat.DAILY -> "Todos los días"
        Repeat.WEEKDAYS -> "De lunes a viernes"
        Repeat.WEEKLY -> "Cada " + Fechas.diaSemana(date.dayOfWeek)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("date", date.toString())
        put("hour", hour)
        put("minute", minute)
        put("repeat", repeat.name)
        put("done", JSONArray(doneDates.toList()))
        put("snooze", snoozeUntil)
        put("snoozeDate", snoozeDate)
    }

    companion object {
        val ORDEN: Comparator<Task> = compareBy<Task>(
            { if (it.hasTime) 0 else 1 },
            { it.hour },
            { it.minute },
            { it.title.lowercase() }
        )

        fun fromJson(o: JSONObject): Task {
            val done = mutableSetOf<String>()
            val arr = o.optJSONArray("done")
            if (arr != null) for (i in 0 until arr.length()) done.add(arr.getString(i))
            val rep = try {
                Repeat.valueOf(o.optString("repeat", "NONE"))
            } catch (e: Exception) {
                Repeat.NONE
            }
            return Task(
                id = o.getLong("id"),
                title = o.getString("title"),
                date = LocalDate.parse(o.getString("date")),
                hour = o.optInt("hour", -1),
                minute = o.optInt("minute", 0),
                repeat = rep,
                doneDates = done,
                snoozeUntil = o.optLong("snooze", 0L),
                snoozeDate = o.optString("snoozeDate", "")
            )
        }
    }
}
