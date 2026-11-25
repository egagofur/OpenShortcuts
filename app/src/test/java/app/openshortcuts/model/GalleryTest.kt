package app.openshortcuts.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryTest {
    @Test fun everyItemIsWellFormed() {
        assertTrue(Gallery.items.size >= 10)
        Gallery.items.forEach { item ->
            val (s, auto) = item.build()
            assertTrue(item.title, item.section in Gallery.sections)
            assertTrue(item.title, s.actions.isNotEmpty())
            val open = ArrayDeque<ActionType>()
            s.actions.forEach { a ->
                when {
                    a.type.opensBlock -> open.addLast(a.type)
                    a.type.isMidBlock -> assertEquals(item.title, a.type.opener, open.lastOrNull())
                    a.type.closesBlock -> assertEquals(item.title, a.type.opener, open.removeLastOrNull())
                }
                val known = a.type.params.map { it.key }.toSet()
                assertTrue("${item.title}: ${a.type} ${a.params.keys - known}", (a.params.keys - known).isEmpty())
            }
            assertTrue(item.title, open.isEmpty())
            if (auto != null) {
                assertEquals(item.title, s.id, auto.shortcutId)
                assertFalse("${item.title} automation must start off", auto.enabled)
            }
        }
    }

    @Test fun buildingTwiceGivesFreshIds() {
        val item = Gallery.items.first()
        val a = item.build().first
        val b = item.build().first
        assertNotEquals(a.id, b.id)
        assertNotEquals(a.actions.first().id, b.actions.first().id)
    }
}
