package com.midia.organizador

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import java.time.LocalDate

/** Guarda las tareas y los ajustes en el propio móvil. */
object TaskStore {
    private const val PREFS = "mi_dia"
    private const val KEY_TASKS = "tareas"

    const val K_M_ON = "m_on"
    const val K_M_H = "m_h"
    const val K_M_M = "m_m"
    const val K_E_ON = "e_on"
    const val K_E_H = "e_h"
    const val K_E_M = "e_m"

    private var lastId = 0L

    fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): MutableList<Task> {
        val raw = prefs(ctx).getString(KEY_TASKS, "[]") ?: "[]"
        val arr = try {
            JSONArray(raw)
        } catch (e: Exception) {
            JSONArray()
        }
        val list = mutableListOf<Task>()
        for (i in 0 until arr.length()) {
            try {
                list.add(Task.fromJson(arr.getJSONObject(i)))
            } catch (_: Exception) {
            }
        }
        return list
    }

    fun save(ctx: Context, list: List<Task>) {
        val hoy = LocalDate.now()
        val limite = hoy.minusDays(60).toString()
        val arr = JSONArray()
        for (t in list) {
            // Las tareas sueltas ya hechas hace más de un mes se borran solas
            if (t.repeat == Repeat.NONE && t.date.isBefore(hoy.minusDays(30)) && t.isDoneOn(t.date)) continue
            t.doneDates.removeAll { it < limite }
            arr.put(t.toJson())
        }
        prefs(ctx).edit().putString(KEY_TASKS, arr.toString()).commit()
    }

    /** Carga, modifica, guarda y reprograma los avisos. */
    @Synchronized
    fun update(ctx: Context, block: (MutableList<Task>) -> Unit) {
        val list = load(ctx)
        block(list)
        save(ctx, list)
        Scheduler.rescheduleAll(ctx)
    }

    @Synchronized
    fun newId(): Long {
        val n = maxOf(System.currentTimeMillis(), lastId + 1)
        lastId = n
        return n
    }
}
