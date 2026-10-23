package io.github.foxsoft2005.stash.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NanoConsentTest {

    @Test fun `asks once, only when there is something to download`() {
        assertTrue(shouldAskForNano(NanoState.NEEDS_DOWNLOAD, NanoConsent.UNASKED))
        assertFalse(shouldAskForNano(NanoState.NEEDS_DOWNLOAD, NanoConsent.ACCEPTED))
        assertFalse(shouldAskForNano(NanoState.NEEDS_DOWNLOAD, NanoConsent.DECLINED))
    }

    @Test fun `never asks when Nano is already there, coming, or unsupported`() {
        for (state in listOf(NanoState.READY, NanoState.DOWNLOADING, NanoState.UNAVAILABLE, NanoState.CHECKING)) {
            assertFalse(state.name, shouldAskForNano(state, NanoConsent.UNASKED))
        }
    }

    @Test fun `startup downloads only after a yes`() {
        assertTrue(shouldDownloadOnStartup(NanoState.NEEDS_DOWNLOAD, NanoConsent.ACCEPTED))
        assertFalse(shouldDownloadOnStartup(NanoState.NEEDS_DOWNLOAD, NanoConsent.UNASKED))
        assertFalse(shouldDownloadOnStartup(NanoState.NEEDS_DOWNLOAD, NanoConsent.DECLINED))
        assertFalse(shouldDownloadOnStartup(NanoState.READY, NanoConsent.ACCEPTED))
    }

    @Test fun `a no can be changed later, but only while a download is possible`() {
        assertTrue(canOfferNano(NanoState.NEEDS_DOWNLOAD))
        assertEquals(
            listOf(NanoState.NEEDS_DOWNLOAD),
            NanoState.entries.filter(::canOfferNano),
        )
    }
}
