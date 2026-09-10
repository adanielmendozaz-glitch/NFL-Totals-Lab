package com.nfltotalslab.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg=Color(0xFF07101C)
val Panel=Color(0xFF0B1727)
val Card=Color(0xFF0D1D30)
val Border=Color(0xFF1B3851)
val Green=Color(0xFF35D19C)
val Blue=Color(0xFF2D9CDB)
val Text=Color(0xFFF2F6FC)
val Muted=Color(0xFF91A5BD)
val Amber=Color(0xFFFFC761)
val Red=Color(0xFFFF6B78)

private val scheme=darkColorScheme(
    primary=Green, secondary=Blue, background=Bg, surface=Panel,
    onPrimary=Color(0xFF022219), onBackground=Text, onSurface=Text,
    outline=Border, error=Red
)

@Composable
fun NflTotalsTheme(content: @Composable () -> Unit){
    MaterialTheme(colorScheme=scheme,typography=Typography(),content=content)
}
