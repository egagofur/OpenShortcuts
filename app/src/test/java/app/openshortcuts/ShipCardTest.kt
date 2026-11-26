package app.openshortcuts

import app.openshortcuts.model.AiAgent
import app.openshortcuts.model.ShipCommit
import app.openshortcuts.model.ShipStats
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShipCardTest {
    private val utc = ZoneOffset.UTC

    @Test
    fun detectsAiCoAuthorsFromTrailers() {
        val msg = "Fix login\n\nCo-Authored-By: Claude Opus <noreply@anthropic.com>\nco-authored-by: Jane <jane@example.com>"
        assertEquals(setOf(AiAgent.Claude), AiAgent.creditedIn(msg))
        assertEquals(setOf(AiAgent.Copilot, AiAgent.ChatGPT),
            AiAgent.creditedIn("x\n\nCo-authored-by: Copilot <c@github.com>\nCo-authored-by: Codex <codex@openai.com>"))
        assertTrue(AiAgent.creditedIn("Mentions Claude in the body only").isEmpty())
    }

    @Test
    fun statsCountOnlyTheWindow() {
        val t = Instant.parse("2026-11-26T09:00:00Z")
        val commits = listOf(
            ShipCommit(t, "a\n\nCo-Authored-By: Claude <x@anthropic.com>", "me/app"),
            ShipCommit(t.plusSeconds(3600), "b", "me/app"),
            ShipCommit(t.plusSeconds(7200), "c\n\nCo-authored-by: Gemini <g@google.com>", "me/site"),
            ShipCommit(t.minus(Duration.ofDays(3)), "old", "me/old"),
        )
        val s = ShipStats.from(commits, t.minusSeconds(60), t.plusSeconds(9000), utc)
        assertEquals(3, s.commits)
        assertEquals(2, s.repos)
        assertEquals(1, s.activeDays)
        assertEquals(Duration.ofHours(2), s.span)
        assertEquals(2f / 3f, s.aiShare, 0.001f)
        assertEquals(listOf(AiAgent.Claude, AiAgent.Gemini), s.agents.map { it.first })
    }

    @Test
    fun pushEventsCountTheirCommits() {
        val t = Instant.parse("2026-11-26T09:00:00Z")
        val s = ShipStats.from(listOf(ShipCommit(t, "push", "p", count = 4)), t.minusSeconds(1), t.plusSeconds(1), utc)
        assertEquals(4, s.commits)
        assertEquals("3h 24m", ShipStats.spanLabel(Duration.ofMinutes(204)))
    }

    @Test
    fun personaTokensFillFromStats() {
        val t = Instant.parse("2026-11-26T22:30:00Z")
        val s = ShipStats.from(listOf(
            ShipCommit(t, "late\n\nCo-Authored-By: Claude <c@anthropic.com>", "me/a"),
            ShipCommit(t.minusSeconds(3 * 3600), "earlier", "me/b"),
        ), t.minusSeconds(24 * 3600), t, utc)
        assertEquals("AI wrote 50%. I brought the vibes.", app.openshortcuts.model.ShipPersona.fill("AI wrote {ai}. I brought the vibes.", s, "ega", utc))
        assertEquals("1 commits after 10pm · Team @ega + Claude", app.openshortcuts.model.ShipPersona.fill("{night} commits after 10pm · Team @{handle} + {agents}", s, "ega", utc))
        assertEquals("Last commit at 22:30", app.openshortcuts.model.ShipPersona.fill("Last commit at {last}", s, "ega", utc))
        assertEquals("Rested 21h 0m, {unknown} stays", app.openshortcuts.model.ShipPersona.fill("Rested {rest}, {unknown} stays", s, "ega", utc))
    }
}
