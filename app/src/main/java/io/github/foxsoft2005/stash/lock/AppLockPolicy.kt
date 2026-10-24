package io.github.foxsoft2005.stash.lock

/** When Stash asks to be unlocked (with App lock on). */
object AppLockPolicy {

    /** Quick trips out (camera app, a message) don't lock; longer breaks do. */
    const val RELOCK_AFTER_MS = 60_000L

    /** Opening the app from scratch. */
    fun lockOnLaunch(enabled: Boolean): Boolean = enabled

    /**
     * Coming back after leaving at [leftAt]. A clock that went backwards locks too:
     * when in doubt, ask.
     */
    fun lockOnReturn(enabled: Boolean, leftAt: Long?, now: Long): Boolean =
        enabled && leftAt != null && (now - leftAt >= RELOCK_AFTER_MS || now < leftAt)
}
