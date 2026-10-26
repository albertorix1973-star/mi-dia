package com.midia.organizador

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import java.time.DayOfWeek
import java.time.LocalDate

/** Acciones comunes: desde la lista, desde la notificación o desde la ventana emergente. */
object TaskActions {
    const val INSIST_MINUTES = 30

    private fun cancelNotif(ctx: Context, id: Long) {
        NotificationManagerCompat.from(ctx).cancel(Scheduler.code(id))
    }

    fun setDone(ctx: Context, id: Long, date: String, done: Boolean) {
        TaskStore.update(ctx) { l ->
            val t = l.find { it.id == id }
            if (t != null) {
                if (done && t.deleteWhenDone) {
                    l.remove(t)
                } else if (done) {
                    t.doneDates.add(date)
                    t.snoozeUntil = 0L
                } else {
                    t.doneDates.remove(date)
                }
            }
        }
        if (done) cancelNotif(ctx, id)
    }

    fun markDone(ctx: Context, id: Long, date: String) = setDone(ctx, id, date, true)

    fun snooze(ctx: Context, id: Long, date: String, minutes: Int) {
        TaskStore.update(ctx) { l ->
            val t = l.find { it.id == id }
            if (t != null) {
                t.snoozeUntil = System.currentTimeMillis() + minutes * 60_000L
                t.snoozeDate = date
            }
        }
        cancelNotif(ctx, id)
    }

    fun moveToTomorrow(ctx: Context, id: Long, date: String) {
        TaskStore.update(ctx) { l ->
            val t = l.find { it.id == id }
            if (t != null) moveOne(l, t, date)
        }
        cancelNotif(ctx, id)
    }

    fun moveAllPendingToTomorrow(ctx: Context, date: String) {
        val d = LocalDate.parse(date)
        TaskStore.update(ctx) { l ->
            for (t in l.toList()) {
                if (t.occursOn(d) && !t.isDoneOn(d)) moveOne(l, t, date)
            }
        }
        val nm = NotificationManagerCompat.from(ctx)
        nm.cancel(Scheduler.CODE_EVENING)
    }

    private fun moveOne(l: MutableList<Task>, t: Task, date: String) {
        val tomorrow = maxOf(LocalDate.parse(date), LocalDate.now()).plusDays(1)
        t.snoozeUntil = 0L
        when {
            t.repeat == Repeat.NONE -> t.date = tomorrow
            // Si la tarea ya se repite mañana, basta con saltar la de hoy
            t.repeat == Repeat.DAILY -> t.doneDates.add(date)
            t.repeat == Repeat.WEEKDAYS && tomorrow.dayOfWeek != DayOfWeek.SATURDAY &&
                tomorrow.dayOfWeek != DayOfWeek.SUNDAY -> t.doneDates.add(date)
            else -> {
                t.doneDates.add(date)
                l.add(Task(TaskStore.newId(), t.title, tomorrow, t.hour, t.minute, insist = t.insist))
            }
        }
    }

    fun delete(ctx: Context, id: Long) {
        TaskStore.update(ctx) { l -> l.removeAll { it.id == id } }
        cancelNotif(ctx, id)
    }
}
