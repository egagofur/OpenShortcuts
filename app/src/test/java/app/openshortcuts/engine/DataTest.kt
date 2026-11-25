package app.openshortcuts.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale
import kotlin.random.Random

class DataTest {
    private val now = LocalDateTime.of(2026, 11, 25, 6, 40, 15)

    @Test fun replacePlainAndRegex() {
        assertEquals("a-b-c", Data.replace("a b c", " ", "-", regex = false))
        assertEquals("x x", Data.replace("12 345", "\\d+", "x", regex = true))
    }

    @Test fun splitAndCombine() {
        assertEquals("a\nb\nc", Data.split("a, b,c", "commas"))
        assertEquals("a b c", Data.combine("a\nb\n\nc", "spaces"))
        assertEquals("h\ni", Data.split("hi", "characters"))
    }

    @Test fun extractAndMatch() {
        assertEquals("12\n3.5", Data.extract("pay 12 then 3.5", "Numbers"))
        assertEquals("https://x.com/a", Data.extract("see https://x.com/a now", "URLs"))
        assertEquals("+62 812-3456-7890", Data.extract("call +62 812-3456-7890 ok", "Phone Numbers"))
        assertEquals("cat\ncar", Data.match("cat dog car", "ca\\w"))
    }

    @Test fun caseChange() {
        assertEquals("Hello World", Data.changeCase("hELLO wORLD", "Title Case"))
        assertEquals("Hello world", Data.changeCase("HELLO WORLD", "Sentence case"))
    }

    @Test fun listItemsAndCount() {
        val list = "a\nb\nc"
        assertEquals("a", Data.item(list, "1"))
        assertEquals("c", Data.item(list, "last"))
        assertEquals("b", Data.item(list, "-2"))
        assertEquals(3, Data.count(list, "items"))
        assertEquals(2, Data.count("two words", "words"))
        assert(Data.item(list, "random", Random(1)) in listOf("a", "b", "c"))
    }

    @Test(expected = IllegalStateException::class) fun itemOutOfRange() { Data.item("a\nb", "5") }

    @Test fun dictionaries() {
        val d = Data.dictionary("name: Ega\ncity: Jakarta")
        assertEquals("Ega", Data.getValue(d, "name"))
        assertEquals("Bandung", Data.getValue(Data.setValue(d, "city", "Bandung"), "city"))
        assertEquals("b", Data.getValue("""{"items":[{"t":"a"},{"t":"b"}]}""", "items.1.t"))
        assertEquals("", Data.getValue(d, "missing"))
    }

    @Test fun calculate() {
        assertEquals("7", Data.number(Data.calculate("1 + 2 * 3")))
        assertEquals("9", Data.number(Data.calculate("(1 + 2) * 3")))
        assertEquals("-1", Data.number(Data.calculate("-(4 - 3)")))
        assertEquals("8", Data.number(Data.calculate("2 ^ 3")))
        assertEquals("2.5", Data.number(Data.calculate("5 / 2")))
        assertEquals("1.5", Data.number(Data.calculate("0,5 × 3")))
        assertEquals("3.14", Data.round("3.14159", 2))
    }

    @Test(expected = IllegalStateException::class) fun divideByZero() { Data.number(Data.calculate("1/0")) }

    @Test fun dates() {
        assertEquals("2026-11-25T06:40:15", Data.date("now", now))
        assertEquals("2026-11-25T23:00:00", Data.date("23:00", now))
        assertEquals("2026-11-26T00:00:00", Data.date("tomorrow", now))
        assertEquals("2026-11-26T09:00:00", Data.date("tomorrow 09:00", now))
        assertEquals("2026-11-25T07:05:00", Data.date("today 7:05", now))
        assertEquals("2026-11-27T23:00:00", Data.adjust("2026-11-25 23:00", "Add", "2", "days", now))
        assertEquals("90", Data.between("2026-11-25 22:00", "2026-11-25 23:30", "minutes", now))
        assertEquals("2026-11-25", Data.format("today", "yyyy-MM-dd", now, Locale.US))
        assertEquals("Wednesday", Data.format("today", "Day name", now, Locale.US))
    }
}
