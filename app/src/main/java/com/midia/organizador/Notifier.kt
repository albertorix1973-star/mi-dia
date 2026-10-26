package com.midia.organizador

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate

object Notifier {
    private const val CH_TASKS = "avisos_tareas"
    private const val CH_SUMMARY = "resumen_diario"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val avisos = NotificationChannel(CH_TASKS, "Avisos de tareas", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Ventana emergente cuando llega la hora de una tarea"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val resumen = NotificationChannel(CH_SUMMARY, "Resumen de la mañana y repaso de la tarde", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Lista de tareas del día y tareas que quedan pendientes"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(avisos)
        nm.createNotificationChannel(resumen)
    }

    private fun openApp(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun actionPi(ctx: Context, action: String, id: Long, date: String, code: Int): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .setAction(action)
            .putExtra(Scheduler.EXTRA_ID, id)
            .putExtra(Scheduler.EXTRA_DATE, date)
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun notify(ctx: Context, id: Int, n: Notification) {
        try {
            val nm = NotificationManagerCompat.from(ctx)
            if (nm.areNotificationsEnabled()) nm.notify(id, n)
        } catch (e: SecurityException) {
        }
    }

    private fun line(t: Task): String = (if (t.hasTime) t.timeText() + "   " else "•   ") + t.title

    /** Aviso de una tarea: notificación emergente con botones + ventana a pantalla completa si el móvil está bloqueado. */
    fun showTask(ctx: Context, t: Task, date: String) {
        createChannels(ctx)
        val code = Scheduler.code(t.id)
        val full = Intent(ctx, ReminderActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            .putExtra(Scheduler.EXTRA_ID, t.id)
            .putExtra(Scheduler.EXTRA_DATE, date)
        val fullPi = PendingIntent.getActivity(ctx, code, full, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = if (t.hasTime) "Hora: ${t.timeText()}" else "Tarea pendiente"

        val n = NotificationCompat.Builder(ctx, CH_TASKS)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(ContextCompat.getColor(ctx, R.color.verde))
            .setContentTitle(t.title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .setFullScreenIntent(fullPi, true)
            .addAction(0, "Hecho", actionPi(ctx, Scheduler.ACTION_DONE, t.id, date, code))
            .addAction(0, "+10 min", actionPi(ctx, Scheduler.ACTION_SNOOZE, t.id, date, code))
            .addAction(0, "Mañana", actionPi(ctx, Scheduler.ACTION_TOMORROW, t.id, date, code))
            .build()
        notify(ctx, code, n)
    }

    fun showMorning(ctx: Context) {
        createChannels(ctx)
        val hoy = LocalDate.now()
        val tasks = TaskStore.load(ctx).filter { it.occursOn(hoy) && !it.isDoneOn(hoy) }.sortedWith(Task.ORDEN)
        val title = when (tasks.size) {
            0 -> "Buenos días ☀️ Hoy no tienes tareas"
            1 -> "Buenos días ☀️ Hoy tienes 1 tarea"
            else -> "Buenos días ☀️ Hoy tienes ${tasks.size} tareas"
        }
        val style = NotificationCompat.InboxStyle()
        tasks.take(7).forEach { style.addLine(line(it)) }
        if (tasks.size > 7) style.setSummaryText("y ${tasks.size - 7} más")

        val b = NotificationCompat.Builder(ctx, CH_SUMMARY)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(ContextCompat.getColor(ctx, R.color.verde))
            .setContentTitle(title)
            .setContentText(if (tasks.isEmpty()) "Pulsa para añadir alguna" else tasks.take(3).joinToString(" · ") { line(it) })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
        if (tasks.isNotEmpty()) b.setStyle(style)
        notify(ctx, Scheduler.CODE_MORNING, b.build())
    }

    fun showEvening(ctx: Context) {
        createChannels(ctx)
        val hoy = LocalDate.now()
        val all = TaskStore.load(ctx).filter { it.occursOn(hoy) }
        if (all.isEmpty()) return
        val pending = all.filter { !it.isDoneOn(hoy) }.sortedWith(Task.ORDEN)
        val b = NotificationCompat.Builder(ctx, CH_SUMMARY)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(ContextCompat.getColor(ctx, R.color.verde))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
        if (pending.isEmpty()) {
            b.setContentTitle("¡Has terminado todas las tareas de hoy! 👏")
                .setContentText("Buen trabajo")
        } else {
            val style = NotificationCompat.InboxStyle()
            pending.take(7).forEach { style.addLine(line(it)) }
            if (pending.size > 7) style.setSummaryText("y ${pending.size - 7} más")
            val n = pending.size
            b.setContentTitle(if (n == 1) "Te queda 1 tarea de hoy" else "Te quedan $n tareas de hoy")
                .setContentText(pending.take(3).joinToString(" · ") { it.title })
                .setStyle(style)
                .addAction(0, "Pasar todo a mañana",
                    actionPi(ctx, Scheduler.ACTION_ALL_TOMORROW, -1L, hoy.toString(), Scheduler.CODE_EVENING))
        }
        notify(ctx, Scheduler.CODE_EVENING, b.build())
    }
}
