package com.xiaohonghua.music

import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

internal fun appColorScheme(theme: Int,night: Boolean): ColorScheme {
    val flower=accents[theme.coerceIn(accents.indices)]
    val accent=if(night) lerp(flower,Color.White,.22f) else flower
    val background=if(night) Color(0xFF161C19) else Color(0xFFF8F8F6)
    val surface=if(night) Color(0xFF202823) else Color.White
    val foreground=if(night) Color(0xFFE8EDE8) else Color(0xFF272B29)
    val muted=if(night) Color(0xFFB0B9B2) else Color(0xFF696F6B)
    val onAccent=if(night) Color(0xFF29181B) else Color.White
    val fixed=lerp(Color.White,flower,.18f)
    val fixedDim=lerp(Color.White,flower,.36f)
    val base=if(night) darkColorScheme() else lightColorScheme()
    // Popup menus, dialogs and sheets use container roles, not just surface.
    return base.copy(primary=accent,onPrimary=onAccent,
        primaryContainer=lerp(background,accent,.16f),onPrimaryContainer=accent,
        secondary=accent,onSecondary=onAccent,secondaryContainer=accent,onSecondaryContainer=onAccent,
        tertiary=accent,onTertiary=onAccent,tertiaryContainer=accent,onTertiaryContainer=onAccent,
        background=background,onBackground=foreground,surface=surface,onSurface=foreground,
        surfaceTint=accent,surfaceVariant=lerp(surface,accent,.10f),onSurfaceVariant=muted,
        surfaceDim=if(night) background else lerp(surface,Color.Black,.10f),
        surfaceBright=if(night) lerp(surface,Color.White,.06f) else surface,
        surfaceContainerLowest=background,surfaceContainerLow=lerp(surface,accent,.025f),
        surfaceContainer=lerp(surface,accent,.04f),surfaceContainerHigh=lerp(surface,accent,.07f),
        surfaceContainerHighest=lerp(surface,accent,.10f),
        outline=muted.copy(alpha=.65f),outlineVariant=lerp(surface,muted,.20f),
        inverseSurface=foreground,inverseOnSurface=background,inversePrimary=if(night) flower else lerp(flower,Color.White,.35f),
        primaryFixed=fixed,primaryFixedDim=fixedDim,onPrimaryFixed=Color(0xFF272B29),onPrimaryFixedVariant=flower,
        secondaryFixed=fixed,secondaryFixedDim=fixedDim,onSecondaryFixed=Color(0xFF272B29),onSecondaryFixedVariant=flower,
        tertiaryFixed=fixed,tertiaryFixedDim=fixedDim,onTertiaryFixed=Color(0xFF272B29),onTertiaryFixedVariant=flower)
}
