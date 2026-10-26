package com.midia.organizador

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object Fechas {
    val ES: Locale = Locale.forLanguageTag("es-ES")
    private val LARGA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)
    private val HORA = DateTimeFormatter.ofPattern("HH:mm")

    fun larga(d: LocalDate): String = LARGA.format(d).replaceFirstChar { it.uppercase() }

    fun hora(ms: Long): String = HORA.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

    fun hhmm(h: Int, m: Int): String = String.format(Locale.ROOT, "%02d:%02d", h, m)

    fun diaSemana(d: DayOfWeek): String = d.getDisplayName(TextStyle.FULL, ES)
}
