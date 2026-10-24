package io.github.foxsoft2005.stash.ui.home

import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.foxsoft2005.stash.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * The All · Lent out · Places chips must never run under the sort button or off
 * the edge, in any language, on a narrow phone with large fonts. (Regression:
 * in Russian, "Места" was hidden under the sort button.)
 */
@RunWith(AndroidJUnit4::class)
class HomeFilterRowLayoutTest {

    @get:Rule val compose = createComposeRule()

    private fun resources(language: String): Resources {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        return context.createConfigurationContext(config).resources
    }

    private fun overlaps(a: DpRect, b: DpRect) =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    private fun checkLayout(language: String, width: Dp, fontScale: Float) {
        val res = resources(language)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    Box(Modifier.width(width)) {
                        HomeFilterRow(
                            allLabel = res.getString(R.string.home_filter_all, 999),
                            lentLabel = res.getString(R.string.home_filter_lent, 99),
                            placesLabel = res.getString(R.string.places_title),
                            lentOnly = false,
                            onAll = {}, onLentOnly = {}, onPlaces = {},
                            sortButton = {
                                IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null) }
                            },
                        )
                    }
                }
            }
        }
        val sort = compose.onNodeWithTag(HomeFilterTags.SORT).getUnclippedBoundsInRoot()
        val where = "[$language, ${width.value.toInt()}dp, font ×$fontScale]"
        assertTrue("sort button off screen $where: $sort", sort.right <= width + 0.5.dp)
        for (tag in listOf(HomeFilterTags.ALL, HomeFilterTags.LENT, HomeFilterTags.PLACES)) {
            val chip = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue("$tag runs under the sort button $where: $chip vs $sort", !overlaps(chip, sort))
            assertTrue("$tag runs off the edge $where: $chip", chip.right <= width + 0.5.dp && chip.left >= 0.dp)
        }
    }

    // Widths are the row itself: the grid has 16dp padding on each side, so a
    // typical small 360dp phone gives 328dp, and the narrowest (320dp) gives 288dp.
    @Test fun russianOnSmallPhone() = checkLayout("ru", 328.dp, 1f)
    @Test fun russianLargeFontNarrowestPhone() = checkLayout("ru", 288.dp, 1.3f)
    @Test fun englishLargeFontNarrowestPhone() = checkLayout("en", 288.dp, 1.3f)
}
