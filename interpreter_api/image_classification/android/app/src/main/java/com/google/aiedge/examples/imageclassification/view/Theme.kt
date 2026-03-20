package com.google.aiedge.examples.imageclassification.view

import androidx.compose.material.MaterialTheme
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun ApplicationTheme(
    content: @Composable () -> Unit
) {
    val colors = lightColors(
        primary = uteqGreen,
        primaryVariant = darkBlue,
        secondary = uteqGreen,
        background = Color.White,
        surface = Color.White,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color.Black,
        onSurface = Color.Black,
    )

    MaterialTheme(
        colors = colors,
        content = content
    )
}
