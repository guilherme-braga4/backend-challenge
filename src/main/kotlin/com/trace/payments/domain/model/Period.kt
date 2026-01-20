package com.trace.payments.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

enum class Period { DAYTIME, NIGHTTIME, WEEKEND }

fun getPeriod(occurredAt: Instant): Period {
    val date = occurredAt.atZone(ZoneOffset.UTC)
    val day = date.dayOfWeek
    val localTime = date.toLocalTime()
    if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return Period.WEEKEND
    return when {
        !localTime.isBefore(java.time.LocalTime.of(6, 0)) && localTime.isBefore(java.time.LocalTime.of(18, 0)) ->
            Period.DAYTIME
        else -> Period.NIGHTTIME
    }
}

fun calculatePeriodRange(period: Period, occurredAt: Instant): Pair<Instant, Instant> {
    val zdt = occurredAt.atZone(ZoneOffset.UTC)
    val date = zdt.toLocalDate()
    return when (period) {
        Period.DAYTIME -> {
            val start = date.atStartOfDay(ZoneOffset.UTC).plus(6, ChronoUnit.HOURS).toInstant()
            val end = date.atStartOfDay(ZoneOffset.UTC).plus(18, ChronoUnit.HOURS).toInstant()
            start to end
        }
        Period.NIGHTTIME -> {
            val localTime = zdt.toLocalTime()
            val (startDate, endDate) = if (!localTime.isBefore(java.time.LocalTime.of(18, 0))) {
                date to date.plusDays(1)
            } else {
                date.minusDays(1) to date
            }
            val start = startDate.atStartOfDay(ZoneOffset.UTC).plus(18, ChronoUnit.HOURS).toInstant()
            val end = endDate.atStartOfDay(ZoneOffset.UTC).plus(6, ChronoUnit.HOURS).toInstant()
            start to end
        }
        Period.WEEKEND -> {
            val start = date.atStartOfDay(ZoneOffset.UTC).toInstant()
            val end = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
            start to end
        }
    }
}
