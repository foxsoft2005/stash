package io.github.foxsoft2005.stash.ai

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a real (emulated) device: the app has no INTERNET permission, so ML Kit
 * can't upload its usage statistics. That must fail quietly, never crash Stash.
 * CI then force-runs ML Kit's scheduled upload jobs and checks for crashes too.
 */
@RunWith(AndroidJUnit4::class)
class OfflineMlKitTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun appHasNoInternetPermission() {
        assertEquals(
            "INTERNET must be stripped from the merged manifest",
            PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(Manifest.permission.INTERNET),
        )
    }

    @Test
    fun imageLabelingWorksOffline() = runBlocking {
        val tagger = MlKitLabelTagger()
        // Several calls, so ML Kit logs (and queues uploads of) usage events.
        repeat(3) { tagger.suggest(testPhoto()) }
        // Let ML Kit's background logging run before the process is checked by CI.
        Thread.sleep(3_000)
    }

    @Test
    fun geminiNanoStatusCheckDoesNotCrash() = runBlocking {
        // Emulators have no AICore: Nano must just be "unavailable".
        val state = GeminiNanoTagger().refreshStatus()
        assertNotEquals(NanoState.CHECKING, state)
    }

    /** Something ML Kit can actually look at: shapes on a background. */
    private fun testPhoto(): Bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this)
        canvas.drawColor(Color.rgb(200, 180, 140))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(30, 60, 160)
        canvas.drawRect(120f, 160f, 400f, 380f, paint)
        paint.color = Color.rgb(220, 40, 40)
        canvas.drawCircle(256f, 120f, 70f, paint)
    }
}
