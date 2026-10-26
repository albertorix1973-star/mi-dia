package com.midia.organizador

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.time.LocalDate

class MainActivity : AppCompatActivity() {
    private var day: LocalDate = LocalDate.now()
    private lateinit var list: LinearLayout
    private lateinit var txtDate: TextView
    private lateinit var txtSummary: TextView

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { checkSetup() }

    private val monthLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val dia = res.data?.getStringExtra(MonthActivity.EXTRA_DIA)
            if (dia != null) {
                day = LocalDate.parse(dia)
                refresh()
            }
        }

    private val voiceLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    val d = VoiceParser.parse(text, day)
                    TaskDialog.show(this, null, d) { refresh() }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Notifier.createChannels(this)

        list = findViewById(R.id.taskList)
        txtDate = findViewById(R.id.txtDate)
        txtSummary = findViewById(R.id.txtSummary)

        findViewById<View>(R.id.btnPrev).setOnClickListener { day = day.minusDays(1); refresh() }
        findViewById<View>(R.id.btnNext).setOnClickListener { day = day.plusDays(1); refresh() }
        txtDate.setOnClickListener { day = LocalDate.now(); refresh() }
        findViewById<View>(R.id.btnAdd).setOnClickListener {
            TaskDialog.show(this, null, Draft("", day)) { refresh() }
        }
        findViewById<View>(R.id.btnVoice).setOnClickListener { startVoice() }
        findViewById<View>(R.id.btnSettings).setOnClickListener { AjustesDialog.show(this) }
        findViewById<View>(R.id.btnMonth).setOnClickListener {
            monthLauncher.launch(Intent(this, MonthActivity::class.java))
        }

        Scheduler.rescheduleAll(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            checkSetup()
        }
    }

    override fun onResume() {
        super.onResume()
        TaskStore.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)
        refresh()
    }

    override fun onPause() {
        TaskStore.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onPause()
    }

    /** Una vez al día, si falta algún permiso para que los avisos lleguen, lo dice. */
    private fun checkSetup() {
        val p = TaskStore.prefs(this)
        val hoy = LocalDate.now().toString()
        if (p.getString("aviso_permisos", "") != hoy && Permisos.pendientes(this).isNotEmpty()) {
            p.edit().putString("aviso_permisos", hoy).apply()
            Permisos.revisar(this, true)
        }
    }

    private fun startVoice() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Ej.: Llamar al gestor mañana a las 11 y media")
        }
        try {
            voiceLauncher.launch(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Este móvil no tiene reconocimiento de voz. Instala la app de Google.", Toast.LENGTH_LONG).show()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun refresh() {
        val hoy = LocalDate.now()
        val tasks = TaskStore.load(this).filter { it.occursOn(day) }.sortedWith(Task.ORDEN)

        val prefijo = when (day) {
            hoy -> "Hoy · "
            hoy.plusDays(1) -> "Mañana · "
            hoy.minusDays(1) -> "Ayer · "
            else -> ""
        }
        txtDate.text = prefijo + Fechas.larga(day)

        val done = tasks.count { it.isDoneOn(day) }
        val pending = tasks.size - done
        txtSummary.text = when {
            tasks.isEmpty() -> "Sin tareas"
            pending == 0 -> "¡Todo hecho! ($done)"
            else -> "$pending pendientes · $done hechas"
        }

        list.removeAllViews()
        if (tasks.isEmpty()) {
            val tv = TextView(this).apply {
                text = "No hay tareas para este día.\n\nPulsa + para escribir una\no «Dictar» para decirla en voz alta."
                gravity = Gravity.CENTER
                textSize = 16f
                setPadding(dp(16), dp(56), dp(16), dp(16))
            }
            list.addView(tv)
            return
        }

        val now = System.currentTimeMillis()
        for (t in tasks) {
            val v = layoutInflater.inflate(R.layout.item_task, list, false)
            val check = v.findViewById<CheckBox>(R.id.check)
            val title = v.findViewById<TextView>(R.id.txtTitle)
            val time = v.findViewById<TextView>(R.id.txtTime)
            val extra = v.findViewById<TextView>(R.id.txtExtra)

            val isDone = t.isDoneOn(day)
            check.isChecked = isDone
            title.text = t.title
            title.paintFlags = if (isDone) title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            else title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            v.alpha = if (isDone) 0.5f else 1f
            time.text = if (t.hasTime) t.timeText() else "—"

            val info = mutableListOf<String>()
            if (t.repeat != Repeat.NONE) info.add("🔁 " + t.repeatText())
            if (t.insist) info.add("🔔 cada ${TaskActions.INSIST_MINUTES} min")
            if (t.snoozeUntil > now && t.snoozeDate == day.toString()) info.add("⏰ próximo aviso " + Fechas.hora(t.snoozeUntil))
            extra.text = info.joinToString("   ")
            extra.visibility = if (info.isEmpty()) View.GONE else View.VISIBLE

            check.setOnClickListener {
                if (check.isChecked && t.deleteWhenDone) {
                    Toast.makeText(this, "Tarea realizada y borrada ✅", Toast.LENGTH_SHORT).show()
                }
                TaskActions.setDone(this, t.id, day.toString(), check.isChecked)
            }
            v.setOnClickListener { TaskDialog.show(this, t, null) { refresh() } }
            v.setOnLongClickListener {
                confirmDelete(t)
                true
            }
            list.addView(v)
        }
    }

    private fun confirmDelete(t: Task) {
        AlertDialog.Builder(this)
            .setTitle("¿Eliminar la tarea?")
            .setMessage(t.title + if (t.repeat != Repeat.NONE) "\n\n(Se borrarán todas las repeticiones)" else "")
            .setPositiveButton("Eliminar") { _, _ -> TaskActions.delete(this, t.id) }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
