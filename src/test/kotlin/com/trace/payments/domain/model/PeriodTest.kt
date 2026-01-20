package com.trace.payments.domain.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class PeriodTest {

    @Test
    fun fiveFiftyNineOnWeekdayIsNighttime() {
        assertEquals(Period.NIGHTTIME, getPeriod(Instant.parse("2024-08-26T05:59:59Z")))
    }

    @Test
    fun sixAmOnWeekdayIsDaytime() {
        assertEquals(Period.DAYTIME, getPeriod(Instant.parse("2024-08-26T06:00:00Z")))
    }

    @Test
    fun fiveFiftyNinePmOnWeekdayIsDaytime() {
        assertEquals(Period.DAYTIME, getPeriod(Instant.parse("2024-08-26T17:59:59Z")))
    }

    @Test
    fun sixPmOnWeekdayIsNighttime() {
        assertEquals(Period.NIGHTTIME, getPeriod(Instant.parse("2024-08-26T18:00:00Z")))
    }

    @Test
    fun saturdayIsWeekend() {
        assertEquals(Period.WEEKEND, getPeriod(Instant.parse("2024-08-24T12:00:00Z")))
    }

    @Test
    fun sundayIsWeekend() {
        assertEquals(Period.WEEKEND, getPeriod(Instant.parse("2024-08-25T12:00:00Z")))
    }

    @Test
    fun midnightOnWeekdayIsNighttime() {
        assertEquals(Period.NIGHTTIME, getPeriod(Instant.parse("2024-08-26T00:00:00Z")))
    }

    @Test
    fun calculatePeriodRangeDaytime() {
        val occurredAt = Instant.parse("2024-08-26T10:00:00Z")
        val (start, end) = calculatePeriodRange(Period.DAYTIME, occurredAt)
        assertEquals(Instant.parse("2024-08-26T06:00:00Z"), start)
        assertEquals(Instant.parse("2024-08-26T18:00:00Z"), end)
    }

    @Test
    fun calculatePeriodRangeNighttimeAfter18h() {
        val occurredAt = Instant.parse("2024-08-26T22:00:00Z")
        val (start, end) = calculatePeriodRange(Period.NIGHTTIME, occurredAt)
        assertEquals(Instant.parse("2024-08-26T18:00:00Z"), start)
        assertEquals(Instant.parse("2024-08-27T06:00:00Z"), end)
    }

    @Test
    fun calculatePeriodRangeNighttimeBefore06h() {
        val occurredAt = Instant.parse("2024-08-26T02:00:00Z")
        val (start, end) = calculatePeriodRange(Period.NIGHTTIME, occurredAt)
        assertEquals(Instant.parse("2024-08-25T18:00:00Z"), start)
        assertEquals(Instant.parse("2024-08-26T06:00:00Z"), end)
    }

    @Test
    fun calculatePeriodRangeWeekend() {
        val occurredAt = Instant.parse("2024-08-24T14:00:00Z")
        val (start, end) = calculatePeriodRange(Period.WEEKEND, occurredAt)
        assertEquals(Instant.parse("2024-08-24T00:00:00Z"), start)
        assertEquals(Instant.parse("2024-08-25T00:00:00Z"), end)
    }
}
