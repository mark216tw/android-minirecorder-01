package com.vibecode.simplerecorder.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp

private val DefaultTypography = Typography()

val RecorderTypography = DefaultTypography.copy(
    displayLarge = DefaultTypography.displayLarge.copy(fontSize = 114.sp),
)
