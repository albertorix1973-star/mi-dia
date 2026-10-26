package com.midia.organizador

import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Todos los avisos del mes, agrupados por día. Al tocar un día se abre ese día en la pantalla principal. */
class MonthActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_DIA = "dia"
        private val FMT_MES = DateTimeFormatter.ofPattern("MMMM yyyy", Fechas.ES)
        private val FMT_DIA = DateTimeFormatter.ofPattern("EEEE d", Fechas.ES)
    }

    private var month: YearMonth = YearMonth.now()
    private lateinit var list: LinearLayout
    private lateinit var txtMonth: TextView
    private lateinit var txtSummary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_month)
        list = findViewById(R.id.monthList)
        txtMonth = findViewById(R.id.txtMonth)
        txtSummary = findViewById(R.id.txtMonthSummary)
        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnPrevMonth).setOnClickListener { month = month.minusMonths(1); refresh() }
        findViewById<TextView>(R.id.btnNextMonth).setOnClickListener { month = month.plusMonths(1); refresh() }
        txtMonth.setOnClickListener { month = YearMonth.now(); refresh() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun openDay(d: LocalDate) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_DIA, d.toString()))
        finish()
    }

    private fun refresh() {
        txtMonth.text = FMT_MES.format(month).replaceFirstChar { it.uppercase() }
        val tasks = TaskStore.load(this)
        val hoy = LocalDate.now()
        val verde = ContextCompat.getColor(this, R.color.verde)
        list.removeAllViews()

        var total = 0
        var pendientes = 0
        for (n in 1..month.lengthOfMonth()) {
            val d = month.atDay(n)
            val delDia = tasks.filter { it.occursOn(d) }.sortedWith(Task.ORDEN)
            if (delDia.isEmpty()) continue

            val header = TextView(this).apply {
                text = FMT_DIA.format(d).replaceFirstChar { it.uppercase() } + if (d == hoy) "  · Hoy" else ""
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(verde)
                setPadding(0, dp(18), 0, dp(4))
                if (d.isBefore(hoy)) alpha = 0.6f
                setOnClickListener { openDay(d) }
            }
            list.addView(header)

            for (t in delDia) {
                total++
                val done = t.isDoneOn(d)
                if (!done) pendientes++
                val iconos = buildString {
                    if (t.insist) append("  🔔")
                    if (t.repeat != Repeat.NONE) append("  🔁")
                }
                val row = TextView(this).apply {
                    text = (if (t.hasTime) t.timeText() else "  —  ") + "    " + t.title + iconos
                    textSize = 16f
                    setPadding(dp(8), dp(8), 0, dp(8))
                    gravity = Gravity.CENTER_VERTICAL
                    if (done) {
                        paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                        alpha = 0.5f
                    }
                    setOnClickListener { openDay(d) }
                }
                list.addView(row)
            }
        }

        txtSummary.text = when (total) {
            0 -> "Sin avisos este mes"
            1 -> "1 aviso · $pendientes pendiente"
            else -> "$total avisos · $pendientes pendientes"
        }
        if (total == 0) {
            list.addView(TextView(this).apply {
                text = "No hay tareas en este mes."
                gravity = Gravity.CENTER
                textSize = 16f
                setPadding(dp(16), dp(56), dp(16), dp(16))
            })
        }
    }
}
