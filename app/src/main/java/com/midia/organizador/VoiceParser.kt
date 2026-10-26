package com.midia.organizador

import java.time.DayOfWeek
import java.time.LocalDate

/** Borrador de tarea que sale del dictado (se confirma en el formulario). */
data class Draft(
    val title: String,
    val date: LocalDate,
    val hour: Int = -1,
    val minute: Int = 0,
    val repeat: Repeat = Repeat.NONE
)

private fun limpiar(t: String): String =
    t.replace(Regex("[,;!?¿¡]"), " ").replace(Regex("\\.(?=\\s|$)"), " ")

/** Texto que se va "consumiendo": lo reconocido (hora, fecha...) se quita del título. */
private class Cursor(texto: String) {
    var orig: String = " " + limpiar(texto) + " "
    var low: String = orig.lowercase(Fechas.ES)

    init {
        if (low.length != orig.length) orig = low
    }

    fun take(r: Regex): MatchResult? {
        val m = r.find(low) ?: return null
        val a = m.range.first
        val b = m.range.last + 1
        low = low.substring(0, a) + " " + low.substring(b)
        orig = orig.substring(0, a) + " " + orig.substring(b)
        return m
    }
}

/**
 * Entiende frases como:
 *  "Llamar al gestor mañana a las 11 y media"
 *  "Sacar la basura todos los días a las 9 de la noche"
 *  "Reunión el viernes a las 17:30"
 *  "Gimnasio cada lunes a las 7"
 *  "Pagar el seguro el 5 de noviembre"
 */
object VoiceParser {
    private const val DIAS = "lunes|martes|mi[ée]rcoles|jueves|viernes|s[áa]bado|domingo"
    private const val HN = "\\d{1,2}|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce"
    private const val MESES = "enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre"
    private val NUM = mapOf(
        "una" to 1, "uno" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5, "seis" to 6,
        "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10, "once" to 11, "doce" to 12
    )

    private fun w(p: String) = Regex("(?<=\\s)(?:$p)(?=\\s)")

    private fun num(x: String): Int = x.toIntOrNull() ?: NUM[x] ?: -1

    private fun dow(name: String): DayOfWeek = when (name.replace('é', 'e').replace('á', 'a')) {
        "lunes" -> DayOfWeek.MONDAY
        "martes" -> DayOfWeek.TUESDAY
        "miercoles" -> DayOfWeek.WEDNESDAY
        "jueves" -> DayOfWeek.THURSDAY
        "viernes" -> DayOfWeek.FRIDAY
        "sabado" -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }

    private fun nextOrToday(hoy: LocalDate, d: DayOfWeek): LocalDate {
        var x = hoy
        while (x.dayOfWeek != d) x = x.plusDays(1)
        return x
    }

    private fun nextAfter(hoy: LocalDate, d: DayOfWeek): LocalDate = nextOrToday(hoy.plusDays(1), d)

    private fun fechaDia(hoy: LocalDate, dia: Int, mes: String, anio: String): LocalDate? = try {
        if (mes.isEmpty()) {
            val d = LocalDate.of(hoy.year, hoy.monthValue, dia)
            if (d.isBefore(hoy)) d.plusMonths(1) else d
        } else {
            val idx = MESES.split("|").indexOf(mes)
            val m = if (idx >= 9) idx else idx + 1   // "septiembre" y "setiembre" son el mes 9
            val y = anio.toIntOrNull() ?: hoy.year
            val d = LocalDate.of(y, m, dia)
            if (anio.isEmpty() && d.isBefore(hoy)) d.plusYears(1) else d
        }
    } catch (e: Exception) {
        null
    }

    fun parse(texto: String, porDefecto: LocalDate, hoy: LocalDate = LocalDate.now()): Draft {
        val c = Cursor(texto)
        var date: LocalDate? = null
        var repeat = Repeat.NONE
        var hour = -1
        var minute = 0

        // 1) Hora
        val mt = c.take(w(
            "(?:a las|a la|sobre las|sobre la|hacia las|para las|las|la) ($HN)(?:[:.h](\\d{2}))?" +
                "(?: (y media|y cuarto|menos cuarto|en punto|y (\\d{1,2})(?: minutos)?))?(?: horas| h)?" +
                "(?: (de la mañana|de la madrugada|de la tarde|de la noche|del mediod[íi]a|por la mañana|por la tarde|por la noche))?"
        )) ?: c.take(w(
            "(\\d{1,2})[:.](\\d{2})()()(?: (de la mañana|de la tarde|de la noche|por la mañana|por la tarde|por la noche))?"
        ))
        if (mt != null) {
            val g = mt.groupValues
            var h = num(g[1])
            var m = g[2].toIntOrNull() ?: 0
            when (g[3]) {
                "y media" -> m = 30
                "y cuarto" -> m = 15
                "menos cuarto" -> {
                    m = 45
                    h = if (h == 1) 12 else h - 1
                }
            }
            if (g[4].isNotEmpty()) m = g[4].toIntOrNull() ?: 0
            val q = g[5]
            if ((q.contains("tarde") || q.contains("noche")) && h in 1..11) h += 12
            if (h in 0..23 && m in 0..59) {
                hour = h
                minute = m
            }
        } else if (c.take(w("a mediod[íi]a|al mediod[íi]a")) != null) {
            hour = 12
            minute = 0
        }
        if (c.take(w("esta (?:tarde|noche)")) != null) {
            date = hoy
            if (hour in 1..11) hour += 12
        }
        if (c.take(w("esta mañana")) != null) {
            date = hoy
        }
        if (c.take(w("(?:por|de) la (?:tarde|noche)")) != null) {
            if (hour in 1..11) hour += 12
        }
        c.take(w("(?:por|de) la mañana|de madrugada"))

        // 2) Repetición
        if (c.take(w("de lunes a viernes|entre semana|(?:todos )?los d[íi]as laborables|d[íi]as laborables|d[íi]as laborales")) != null) {
            repeat = Repeat.WEEKDAYS
        } else if (c.take(w("todos los d[íi]as|cada d[íi]a|a diario|diariamente")) != null) {
            repeat = Repeat.DAILY
        } else {
            val mw = c.take(w("(?:cada|todos los|todas las semanas el|cada semana el) ($DIAS)s?"))
            if (mw != null) {
                repeat = Repeat.WEEKLY
                date = nextOrToday(hoy, dow(mw.groupValues[1]))
            } else if (c.take(w("cada semana|todas las semanas|semanalmente")) != null) {
                repeat = Repeat.WEEKLY
            }
        }

        // 3) Fecha
        if (date == null) {
            if (c.take(w("pasado mañana")) != null) {
                date = hoy.plusDays(2)
            } else if (c.take(w("mañana")) != null) {
                date = hoy.plusDays(1)
            } else if (c.take(w("hoy")) != null) {
                date = hoy
            } else {
                val md = c.take(w("(?:el )?(?:d[íi]a )?(\\d{1,2}) de ($MESES)(?: de (\\d{4}))?"))
                    ?: c.take(w("el (?:d[íi]a )?(\\d{1,2})()()"))
                if (md != null) {
                    date = fechaDia(hoy, md.groupValues[1].toInt(), md.groupValues[2], md.groupValues[3])
                } else {
                    val wd = c.take(w("(?:el |este |esta )?(?:pr[óo]ximo )?($DIAS)(?: que viene| pr[óo]ximo)?"))
                    if (wd != null) date = nextAfter(hoy, dow(wd.groupValues[1]))
                }
            }
        }

        // 4) Título: lo que queda
        var title = c.orig.replace(Regex("\\s+"), " ").trim()
        title = title.replace(
            Regex("^(?:recu[ée]rdame|recordarme|recordar|av[íi]same|tengo que|hay que)(?: de| que)?\\s*", RegexOption.IGNORE_CASE), ""
        )
        title = title.replace(Regex("^(?:que|de)\\s+", RegexOption.IGNORE_CASE), "")
        title = title.replace(Regex("\\s+(?:el|la|los|las|a|al|de|del|para|y|en)$", RegexOption.IGNORE_CASE), "").trim()
        if (title.isEmpty()) title = "Tarea"
        title = title.replaceFirstChar { it.uppercase() }

        val finalDate = date ?: if (repeat != Repeat.NONE) hoy else porDefecto
        return Draft(title, finalDate, hour, minute, repeat)
    }
}
