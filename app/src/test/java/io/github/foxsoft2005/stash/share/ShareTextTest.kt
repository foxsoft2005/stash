package io.github.foxsoft2005.stash.share

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareTextTest {

    private val place = "%1\$s → %2\$s"
    private val lent = "%1\$s → lent to %2\$s (usually: %3\$s)"

    @Test fun `name and place`() {
        assertEquals("Passport → Top drawer, blue box", ShareText.of(" Passport", "Top drawer, blue box ", null, "", place, lent))
    }

    @Test fun `lent items say who has them`() {
        assertEquals("Drill → lent to Mark (usually: Garage)", ShareText.of("Drill", "Garage", "Mark", "", place, lent))
    }

    @Test fun `a note goes on its own line, blank borrower counts as not lent`() {
        assertEquals("Keys → Hook\nblue tag", ShareText.of("Keys", "Hook", "  ", " blue tag ", place, lent))
    }

    @Test fun `russian format`() {
        assertEquals("Дрель → одолжено: Марк (обычно: Гараж)", ShareText.of("Дрель", "Гараж", "Марк", "", "%1\$s → %2\$s", "%1\$s → одолжено: %2\$s (обычно: %3\$s)"))
    }
}
