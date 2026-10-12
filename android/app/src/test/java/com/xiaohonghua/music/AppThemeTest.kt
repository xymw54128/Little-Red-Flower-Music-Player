package com.xiaohonghua.music

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class AppThemeTest {
    @Test fun popupContainersUseTheCurrentThemeInsteadOfMaterialPurpleDefaults() {
        accents.forEachIndexed {index,accent ->
            val scheme=appColorScheme(index,false)
            assertEquals(lerp(Color.White,accent,.04f),scheme.surfaceContainer)
            assertEquals(lerp(Color.White,accent,.07f),scheme.surfaceContainerHigh)
            assertEquals(lerp(Color.White,accent,.10f),scheme.surfaceContainerHighest)
            assertEquals(accent,scheme.surfaceTint)
        }
    }

    @Test fun fixedColorsAlsoFollowEveryThemeInBothModes() {
        for(index in accents.indices) for(night in listOf(false,true)) {
            val scheme=appColorScheme(index,night)
            assertEquals(lerp(Color.White,accents[index],.18f),scheme.primaryFixed)
            assertEquals(scheme.primaryFixed,scheme.secondaryFixed)
            assertEquals(scheme.primaryFixed,scheme.tertiaryFixed)
        }
    }

    @Test fun nightContainersStayDarkWithReadableForegrounds() {
        accents.indices.forEach {index ->
            val scheme=appColorScheme(index,true)
            assertTrue(scheme.surfaceContainer.luminance() < .1f)
            assertTrue(scheme.onSurface.luminance() > .7f)
        }
    }
}
