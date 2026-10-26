package com.midia.organizador

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra(Scheduler.EXTRA_ID, -1L)
        val date = intent.getStringExtra(Scheduler.EXTRA_DATE) ?: LocalDate.now().toString()

        when (intent.action) {
            Scheduler.ACTION_TASK -> {
                val t = TaskStore.load(ctx).find { it.id == id }
                if (t != null && !t.doneDates.contains(date)) {
                    Notifier.showTask(ctx, t, date)
                    if (t.snoozeUntil != 0L && t.snoozeUntil <= System.currentTimeMillis() + 60_000L) {
                        // update() ya reprograma
                        TaskStore.update(ctx) { l -> l.find { it.id == id }?.snoozeUntil = 0L }
                        return
                    }
                }
                Scheduler.rescheduleAll(ctx)
            }
            Scheduler.ACTION_MORNING -> {
                Notifier.showMorning(ctx)
                Scheduler.rescheduleAll(ctx)
            }
            Scheduler.ACTION_EVENING -> {
                Notifier.showEvening(ctx)
                Scheduler.rescheduleAll(ctx)
            }
            Scheduler.ACTION_DONE -> TaskActions.markDone(ctx, id, date)
            Scheduler.ACTION_SNOOZE -> TaskActions.snooze(ctx, id, date, 10)
            Scheduler.ACTION_TOMORROW -> TaskActions.moveToTomorrow(ctx, id, date)
            Scheduler.ACTION_ALL_TOMORROW -> TaskActions.moveAllPendingToTomorrow(ctx, date)
        }
    }
}

/** Tras reiniciar el móvil, cambiar la hora o actualizar la app, vuelve a programar todo. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Notifier.createChannels(ctx)
        Scheduler.rescheduleAll(ctx)
    }
}
