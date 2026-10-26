package com.midia.organizador

import android.app.Activity
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate

/** Formulario para crear o editar una tarea. */
object TaskDialog {
    fun show(act: AppCompatActivity, existing: Task?, draft: Draft?, onSaved: () -> Unit) {
        val v = act.layoutInflater.inflate(R.layout.dialog_task, null)
        val edit = v.findViewById<EditText>(R.id.editTitle)
        val btnDate = v.findViewById<Button>(R.id.btnDate)
        val btnTime = v.findViewById<Button>(R.id.btnTime)
        val btnNoTime = v.findViewById<Button>(R.id.btnNoTime)
        val spin = v.findViewById<Spinner>(R.id.spinRepeat)
        val swInsist = v.findViewById<SwitchCompat>(R.id.swInsist)
        swInsist.isChecked = existing?.insist ?: draft?.insist ?: true

        var date: LocalDate = existing?.date ?: draft?.date ?: LocalDate.now()
        var hour: Int = existing?.hour ?: draft?.hour ?: -1
        var minute: Int = existing?.minute ?: draft?.minute ?: 0
        val repeat: Repeat = existing?.repeat ?: draft?.repeat ?: Repeat.NONE

        edit.setText(existing?.title ?: draft?.title ?: "")
        edit.setSelection(edit.text.length)

        fun paint() {
            btnDate.text = Fechas.larga(date)
            btnTime.text = if (hour >= 0) Fechas.hhmm(hour, minute) else "Elegir hora"
            btnNoTime.visibility = if (hour >= 0) View.VISIBLE else View.GONE
        }
        paint()

        spin.adapter = ArrayAdapter(act, android.R.layout.simple_spinner_dropdown_item, Repeat.values().map { it.label })
        spin.setSelection(repeat.ordinal)

        btnDate.setOnClickListener {
            DatePickerDialog(act, { _, y, m, d ->
                date = LocalDate.of(y, m + 1, d)
                paint()
            }, date.year, date.monthValue - 1, date.dayOfMonth).show()
        }
        btnTime.setOnClickListener {
            TimePickerDialog(act, { _, h, mi ->
                hour = h
                minute = mi
                paint()
            }, if (hour >= 0) hour else 9, minute, true).show()
        }
        btnNoTime.setOnClickListener {
            hour = -1
            paint()
        }

        val b = AlertDialog.Builder(act)
            .setTitle(if (existing == null) "Nueva tarea" else "Editar tarea")
            .setView(v)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar", null)
        if (existing != null) {
            b.setNeutralButton("Eliminar") { _, _ ->
                TaskActions.delete(act, existing.id)
                onSaved()
            }
        }
        val dlg = b.create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = edit.text.toString().trim()
                if (title.isEmpty()) {
                    edit.error = "Escribe la tarea"
                } else {
                    val rep = Repeat.values()[spin.selectedItemPosition]
                    val insist = swInsist.isChecked
                    TaskStore.update(act) { l ->
                        val t = if (existing != null) l.find { it.id == existing.id } else null
                        if (t != null) {
                            t.title = title
                            t.date = date
                            t.hour = hour
                            t.minute = minute
                            t.repeat = rep
                            t.insist = insist
                            t.snoozeUntil = 0L
                        } else {
                            l.add(Task(TaskStore.newId(), title, date, hour, minute, rep, insist = insist))
                        }
                    }
                    val aviso = when {
                        hour < 0 -> "Tarea guardada (sin hora, saldrá en el resumen)"
                        insist -> "Te avisaré a las ${Fechas.hhmm(hour, minute)} y cada ${TaskActions.INSIST_MINUTES} min hasta que la marques como realizada"
                        else -> "Te avisaré a las ${Fechas.hhmm(hour, minute)}"
                    }
                    Toast.makeText(act, aviso, Toast.LENGTH_LONG).show()
                    onSaved()
                    dlg.dismiss()
                }
            }
        }
        dlg.show()
    }
}

/** Ajustes: horas del resumen de la mañana y del repaso de la tarde. */
object AjustesDialog {
    fun show(act: AppCompatActivity) {
        val v = act.layoutInflater.inflate(R.layout.dialog_settings, null)
        val p = TaskStore.prefs(act)

        fun fila(swId: Int, btnId: Int, kOn: String, kH: String, kM: String, defH: Int) {
            val sw = v.findViewById<SwitchCompat>(swId)
            val btn = v.findViewById<Button>(btnId)
            fun pintar() {
                btn.text = Fechas.hhmm(p.getInt(kH, defH), p.getInt(kM, 0))
            }
            sw.isChecked = p.getBoolean(kOn, true)
            pintar()
            sw.setOnCheckedChangeListener { _, on ->
                p.edit().putBoolean(kOn, on).apply()
                Scheduler.rescheduleAll(act)
            }
            btn.setOnClickListener {
                TimePickerDialog(act, { _, h, m ->
                    p.edit().putInt(kH, h).putInt(kM, m).apply()
                    pintar()
                    Scheduler.rescheduleAll(act)
                }, p.getInt(kH, defH), p.getInt(kM, 0), true).show()
            }
        }
        fila(R.id.swMorning, R.id.btnMorning, TaskStore.K_M_ON, TaskStore.K_M_H, TaskStore.K_M_M, 8)
        fila(R.id.swEvening, R.id.btnEvening, TaskStore.K_E_ON, TaskStore.K_E_H, TaskStore.K_E_M, 20)
        v.findViewById<View>(R.id.btnPerms).setOnClickListener { Permisos.revisar(act, false) }

        AlertDialog.Builder(act)
            .setTitle("Ajustes")
            .setView(v)
            .setPositiveButton("Cerrar", null)
            .show()
    }
}

/** Comprueba lo necesario para que los avisos lleguen siempre a su hora. */
object Permisos {
    fun pendientes(ctx: Context): List<Pair<String, Intent>> {
        val res = mutableListOf<Pair<String, Intent>>()
        val pkg = Uri.parse("package:" + ctx.packageName)

        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
            res.add(Pair("Activar las notificaciones", Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)))
        }
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.S || Build.VERSION.SDK_INT == Build.VERSION_CODES.S_V2) {
            val am = ctx.getSystemService(AlarmManager::class.java)
            if (am != null && !am.canScheduleExactAlarms()) {
                res.add(Pair("Permitir alarmas exactas (avisos a su hora)", Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)))
            }
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm != null && !nm.canUseFullScreenIntent()) {
                res.add(Pair("Permitir ventanas a pantalla completa (con el móvil bloqueado)", Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)))
            }
        }
        val pm = ctx.getSystemService(PowerManager::class.java)
        if (pm != null && !pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
            res.add(Pair("Quitar el ahorro de batería para Mi Día (que no se pierdan avisos)", Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)))
        }
        return res
    }

    fun revisar(act: Activity, silencioso: Boolean) {
        val p = pendientes(act)
        if (p.isEmpty()) {
            if (!silencioso) Toast.makeText(act, "Todo listo: los avisos están bien configurados ✅", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(act)
            .setTitle("Para que los avisos lleguen siempre, toca cada opción:")
            .setItems(p.map { it.first }.toTypedArray()) { _, i ->
                try {
                    act.startActivity(p[i].second)
                } catch (e: Exception) {
                    act.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + act.packageName)))
                }
            }
            .setNegativeButton("Más tarde", null)
            .show()
    }
}
