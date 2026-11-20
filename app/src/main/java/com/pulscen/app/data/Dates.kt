package com.pulscen.app.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Dates {
    private val ru = Locale("ru", "RU")
    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", ru)
    private val shortFormatter = DateTimeFormatter.ofPattern("d MMM", ru)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", ru)

    fun nowIso(): String = Instant.now().toString()

    fun parse(value: String?): Instant? =
        value?.let { runCatching { Instant.parse(it) }.getOrNull() }

    fun formatDate(iso: String?): String =
        parse(iso)?.atZone(ZoneId.systemDefault())?.format(dateFormatter) ?: "—"

    fun formatShortDate(iso: String?): String =
        parse(iso)?.atZone(ZoneId.systemDefault())?.format(shortFormatter) ?: "—"

    fun formatDateTime(iso: String?): String =
        parse(iso)?.atZone(ZoneId.systemDefault())?.format(dateTimeFormatter) ?: "—"

    fun isOlderThanHours(iso: String?, hours: Long): Boolean {
        val instant = parse(iso) ?: return true
        return instant.plusSeconds(hours * 3600L).isBefore(Instant.now())
    }
}
