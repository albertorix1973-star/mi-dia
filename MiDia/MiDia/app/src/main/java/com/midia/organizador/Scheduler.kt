package com.midia.organizador

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Programa las alarmas del sistema: una por tarea (su próximo aviso), más el resumen y el repaso. */
object Scheduler {
    const val ACTION_TASK = "com.midia.organizador.TAREA"
    const val ACTION_MORNING = "com.midia.organizador.RESUMEN"
    const val ACTION_EVENING = "com.midia.organizador.REPASO"
    const val ACTION_DONE = "com.midia.organizador.HECHO"
    const val ACTION_SNOOZE = "com.midia.organizador.POSPONER"
    const val ACTION_TOMORROW = "com.midia.organizador.A_MANANA"
    const val ACTION_ALL_TOMORROW = "com.midia.organizador.TODO_A_MANANA"
    const val EXTRA_ID = "id"
    const val EXTRA_DATE = "fecha"
    const val CODE_MORNING = 900001
    const val CODE_EVENING = 900002
    private const val KEY_SCHEDULED = "programadas"

    fun code(id: Long): Int = ((id xor (id ushr 32)).toInt() and 0x7fffffff) % 800000 + 1000

    private fun intent(ctx: Context, action: String): Intent =
        Intent(ctx, AlarmReceiver::class.java).setAction(action)

    private fun pi(ctx: Context, code: Int, i: Intent): PendingIntent =
        PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun cancel(ctx: Context, am: AlarmManager, code: Int, action: String) {
        val p: PendingIntent? = PendingIntent.getBroadcast(
            ctx, code, intent(ctx, action),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (p != null) {
            am.cancel(p)
            p.cancel()
        }
    }

    private fun setAt(am: AlarmManager, ms: Long, p: PendingIntent) {
        try {
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, p)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, p)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, p)
        }
    }

    private fun nextAt(h: Int, m: Int): Long {
        var dt = LocalDate.now().atTime(h, m)
        if (!dt.isAfter(LocalDateTime.now())) dt = dt.plusDays(1)
        return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /** Próximo aviso de una tarea: (momento en ms, fecha de la tarea). */
    fun nextTrigger(t: Task, now: Long): Pair<Long, String>? {
        if (t.snoozeUntil > now) {
            return Pair(t.snoozeUntil, t.snoozeDate.ifEmpty { LocalDate.now().toString() })
        }
        if (!t.hasTime) return null
        var day = LocalDate.now()
        for (i in 0 until 400) {
            if (t.repeat == Repeat.NONE && day.isAfter(t.date)) break
            if (t.occursOn(day) && !t.isDoneOn(day)) {
                val ms = day.atTime(t.hour, t.minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (ms > now) return Pair(ms, day.toString())
            }
            day = day.plusDays(1)
        }
        return null
    }

    fun rescheduleAll(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val prefs = TaskStore.prefs(ctx)

        val old = prefs.getStringSet(KEY_SCHEDULED, emptySet()) ?: emptySet()
        for (c in old) c.toIntOrNull()?.let { cancel(ctx, am, it, ACTION_TASK) }

        val now = System.currentTimeMillis()
        val nuevos = mutableSetOf<String>()
        for (t in TaskStore.load(ctx)) {
            val next = nextTrigger(t, now) ?: continue
            val c = code(t.id)
            val i = intent(ctx, ACTION_TASK)
                .putExtra(EXTRA_ID, t.id)
                .putExtra(EXTRA_DATE, next.second)
            setAt(am, next.first, pi(ctx, c, i))
            nuevos.add(c.toString())
        }
        prefs.edit().putStringSet(KEY_SCHEDULED, nuevos).apply()

        cancel(ctx, am, CODE_MORNING, ACTION_MORNING)
        cancel(ctx, am, CODE_EVENING, ACTION_EVENING)
        if (prefs.getBoolean(TaskStore.K_M_ON, true)) {
            val ms = nextAt(prefs.getInt(TaskStore.K_M_H, 8), prefs.getInt(TaskStore.K_M_M, 0))
            setAt(am, ms, pi(ctx, CODE_MORNING, intent(ctx, ACTION_MORNING)))
        }
        if (prefs.getBoolean(TaskStore.K_E_ON, true)) {
            val ms = nextAt(prefs.getInt(TaskStore.K_E_H, 20), prefs.getInt(TaskStore.K_E_M, 0))
            setAt(am, ms, pi(ctx, CODE_EVENING, intent(ctx, ACTION_EVENING)))
        }
    }
}
