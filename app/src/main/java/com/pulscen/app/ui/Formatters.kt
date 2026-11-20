package com.pulscen.app.ui

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object Money {
    private val symbols = DecimalFormatSymbols(Locale("ru", "RU")).apply {
        groupingSeparator = '\u00A0'
        decimalSeparator = ','
    }
    private val whole = DecimalFormat("#,##0", symbols)
    private val withKopecks = DecimalFormat("#,##0.00", symbols)

    fun rub(value: Double?): String {
        if (value == null) return "—"
        val rounded = Math.round(value * 100.0) / 100.0
        val text = if (rounded % 1.0 == 0.0) whole.format(rounded) else withKopecks.format(rounded)
        return "$text ₽"
    }

    fun amount(value: Double?): String {
        if (value == null) return "—"
        val sign = if (value > 0) "+" else if (value < 0) "−" else ""
        val absolute = kotlin.math.abs(value)
        val text = if (absolute % 1.0 == 0.0) whole.format(absolute) else withKopecks.format(absolute)
        return "$sign$text ₽"
    }

    fun percent(value: Double?): String {
        if (value == null) return "—"
        val sign = if (value > 0) "+" else if (value < 0) "−" else ""
        return "$sign${withKopecks.format(kotlin.math.abs(value))}%"
    }
}
