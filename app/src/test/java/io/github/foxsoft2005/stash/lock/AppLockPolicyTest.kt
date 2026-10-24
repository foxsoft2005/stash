package io.github.foxsoft2005.stash.lock

import io.github.foxsoft2005.stash.lock.AppLockPolicy.RELOCK_AFTER_MS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {

    @Test fun `launch locks only when the lock is on`() {
        assertTrue(AppLockPolicy.lockOnLaunch(enabled = true))
        assertFalse(AppLockPolicy.lockOnLaunch(enabled = false))
    }

    @Test fun `a quick trip out doesn't lock, a minute away does`() {
        assertFalse(AppLockPolicy.lockOnReturn(enabled = true, leftAt = 1_000, now = 1_000 + RELOCK_AFTER_MS - 1))
        assertTrue(AppLockPolicy.lockOnReturn(enabled = true, leftAt = 1_000, now = 1_000 + RELOCK_AFTER_MS))
    }

    @Test fun `never locks when off, or when the app never left`() {
        assertFalse(AppLockPolicy.lockOnReturn(enabled = false, leftAt = 0, now = 10 * RELOCK_AFTER_MS))
        assertFalse(AppLockPolicy.lockOnReturn(enabled = true, leftAt = null, now = 10 * RELOCK_AFTER_MS))
    }

    @Test fun `a clock that went backwards locks`() {
        assertTrue(AppLockPolicy.lockOnReturn(enabled = true, leftAt = 5_000, now = 1_000))
    }
}
