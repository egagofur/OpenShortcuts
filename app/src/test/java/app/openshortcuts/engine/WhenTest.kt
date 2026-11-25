package app.openshortcuts.engine

import app.openshortcuts.model.TimeMode
import app.openshortcuts.model.Automation
import app.openshortcuts.model.TriggerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class WhenTest {
    private val now = LocalDateTime.of(2026, 11, 25, 10, 10)
    private fun auto(f: Automation.() -> Automation) = Automation(kind = TriggerKind.Time).f()
    private val jakarta = When.Place(-6.2, 106.8)
    private val wib = ZoneId.of("Asia/Jakarta")

    @Test fun dailyTimeRollsToTomorrow() =
        assertEquals(LocalDateTime.of(2026, 11, 26, 9, 0), When.next(auto { copy(timeOfDay = LocalTime.of(9, 0)) }, now, null))

    @Test fun chosenDaysSkipToTheNextOne() {
        assertEquals(LocalDateTime.of(2026, 11, 30, 9, 0), When.next(auto { copy(timeOfDay = LocalTime.of(9, 0), days = setOf(java.time.DayOfWeek.MONDAY)) }, now, null))
    }

    @Test fun monthlyClampsToShortMonths() {
        val a = auto { copy(timeOfDay = LocalTime.of(8, 0), monthDay = 31) }
        assertEquals(LocalDateTime.of(2026, 11, 30, 8, 0), When.next(a, now, null))
        assertEquals(LocalDateTime.of(2027, 2, 28, 8, 0), When.next(a, LocalDateTime.of(2027, 2, 1, 0, 0), null))
    }

    @Test fun intervalStepsFromTheWindowStart() {
        val a = auto { copy(timeMode = TimeMode.Every, every = 60, windowStart = LocalTime.of(8, 0), windowEnd = LocalTime.of(22, 0)) }
        assertEquals(LocalDateTime.of(2026, 11, 25, 11, 0), When.next(a, now, null))
        assertEquals(LocalDateTime.of(2026, 11, 25, 22, 0), When.next(a, LocalDateTime.of(2026, 11, 25, 21, 30), null))
        assertEquals(LocalDateTime.of(2026, 11, 26, 8, 0), When.next(a, LocalDateTime.of(2026, 11, 25, 22, 0), null))
    }

    @Test fun intervalAllDay() {
        val a = auto { copy(timeMode = TimeMode.Every, every = 30, windowStart = LocalTime.MIDNIGHT, windowEnd = LocalTime.MIDNIGHT) }
        assertEquals(LocalDateTime.of(2026, 11, 25, 10, 30), When.next(a, now, null))
    }

    @Test fun onceRunsOnceThenNever() {
        val a = auto { copy(timeMode = TimeMode.Once, date = "2026-11-28", timeOfDay = LocalTime.of(7, 0)) }
        assertEquals(LocalDateTime.of(2026, 11, 28, 7, 0), When.next(a, now, null))
        assertNull(When.next(a, LocalDateTime.of(2026, 11, 28, 7, 0), null))
    }

    @Test fun jakartaSunriseAndSunsetAreSensible() {
        val (rise, set) = When.sun(now.toLocalDate(), jakarta, wib)!!
        assertTrue("sunrise $rise", rise.isAfter(LocalTime.of(5, 0)) && rise.isBefore(LocalTime.of(5, 45)))
        assertTrue("sunset $set", set.isAfter(LocalTime.of(17, 30)) && set.isBefore(LocalTime.of(18, 15)))
    }

    @Test fun sunsetOffsetBefore() {
        val a = auto { copy(timeMode = TimeMode.Sunset, offset = -15) }
        val set = When.sun(now.toLocalDate(), jakarta, wib)!!.second
        assertEquals(now.toLocalDate().atTime(set).minusMinutes(15).withSecond(0).withNano(0), When.next(a, now, jakarta, wib))
    }
}
