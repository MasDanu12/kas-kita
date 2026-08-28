package com.example.kaskita.util

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object CurrencyFormatter {
    private val indonesianLocale = Locale("id", "ID")
    private val numberFormat = NumberFormat.getNumberInstance(indonesianLocale).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }

    fun format(amount: Double): String {
        return "Rp" + numberFormat.format(amount)
    }

    fun format(amount: Long): String {
        return "Rp" + numberFormat.format(amount)
    }

    fun formatCompact(amount: Double): String {
        return when {
            amount >= 1_000_000_000 -> String.format(Locale.US, "%.1fM", amount / 1_000_000_000)
            amount >= 1_000_000 -> String.format(Locale.US, "%.1f Jt", amount / 1_000_000)
            amount >= 1_000 -> String.format(Locale.US, "%.0f Rb", amount / 1_000)
            else -> format(amount)
        }
    }
}

object DateUtils {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val displayDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale("id", "ID"))
    private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale("id", "ID"))

    fun todayStr(): String {
        return LocalDate.now().format(dateFormatter)
    }

    fun currentPeriode(): String {
        return LocalDate.now().toString().substring(0, 7) // "YYYY-MM"
    }

    fun currentYear(): String {
        return LocalDate.now().year.toString()
    }

    fun formatDisplayDate(dateStr: String): String {
        return try {
            val parsed = LocalDate.parse(dateStr, dateFormatter)
            parsed.format(displayDateFormatter)
        } catch (e: Exception) {
            dateStr
        }
    }

    fun formatMonthYear(periode: String): String {
        return try {
            val parts = periode.split("-")
            val year = parts[0].toInt()
            val month = parts[1].toInt()
            val date = LocalDate.of(year, month, 1)
            date.format(monthYearFormatter)
        } catch (e: Exception) {
            periode
        }
    }

    fun periodAdd(periode: String, monthsToAdd: Int): String {
        val parts = periode.split("-").map { it.toInt() }
        val totalMonths = parts[0] * 12 + (parts[1] - 1) + monthsToAdd
        val newYear = totalMonths / 12
        val newMonth = (totalMonths % 12) + 1
        return String.format(Locale.US, "%04d-%02d", newYear, newMonth)
    }

    fun periodeFromDateStr(dateStr: String): String {
        val effective = if (dateStr.isBlank()) todayStr() else dateStr
        return if (effective.length >= 7) effective.substring(0, 7) else currentPeriode()
    }

    fun periodeMax(a: String, b: String): String {
        return if (a > b) a else b
    }
}
