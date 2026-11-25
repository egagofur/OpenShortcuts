package app.openshortcuts.model

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

class DaysTest {
    @Test fun calendarNumbersRoundTrip() {
        assertEquals(DayOfWeek.SUNDAY, dayOfWeekFromCalendar(1))
        assertEquals(DayOfWeek.MONDAY, dayOfWeekFromCalendar(2))
        assertEquals(DayOfWeek.SATURDAY, dayOfWeekFromCalendar(7))
        DayOfWeek.entries.forEach { assertEquals(it, dayOfWeekFromCalendar(it.calendarDay)) }
    }

    @Test fun labels() {
        assertEquals("Daily", emptySet<DayOfWeek>().daysLabel())
        assertEquals("Weekdays", WEEK.take(5).toSet().daysLabel())
        assertEquals("Weekends", setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).daysLabel())
        assertEquals("Mo We", setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY).daysLabel())
    }
}
