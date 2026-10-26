package com.midia.organizador

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDate

/** Ventana emergente que aparece cuando llega la hora de una tarea. */
class ReminderActivity : AppCompatActivity() {
    private var id = -1L
    private var date = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        setContentView(R.layout.activity_reminder)
        window.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        setFinishOnTouchOutside(false)

        findViewById<View>(R.id.btnDone).setOnClickListener {
            TaskActions.markDone(this, id, date); finish()
        }
        findViewById<View>(R.id.btnSnooze).setOnClickListener {
            TaskActions.snooze(this, id, date, 10); finish()
        }
        findViewById<View>(R.id.btnTomorrow).setOnClickListener {
            TaskActions.moveToTomorrow(this, id, date); finish()
        }
        findViewById<View>(R.id.btnClose).setOnClickListener { finish() }
        bind(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bind(intent)
    }

    private fun bind(i: Intent) {
        id = i.getLongExtra(Scheduler.EXTRA_ID, -1L)
        date = i.getStringExtra(Scheduler.EXTRA_DATE) ?: LocalDate.now().toString()
        val t = TaskStore.load(this).find { it.id == id }
        if (t == null) {
            finish()
            return
        }
        findViewById<TextView>(R.id.txtTitle).text = t.title
        val cuando = if (t.hasTime) "Programada para las ${t.timeText()}" else "Tarea de hoy"
        findViewById<TextView>(R.id.txtWhen).text =
            if (t.insist) "$cuando\nSe repetirá cada ${TaskActions.INSIST_MINUTES} min hasta que pulses «Tarea realizada»" else cuando
    }
}
