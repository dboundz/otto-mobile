package to.ottomot.driftd.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal fun ottoTypography(): Typography {
    val base = Typography()
    return base.copy(
        displaySmall =
            base.displaySmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                letterSpacing = (-0.65).sp,
            ),
        headlineSmall =
            base.headlineSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
            ),
        headlineMedium =
            base.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold,
            ),
        titleLarge =
            base.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
            ),
        titleMedium =
            base.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
            ),
        titleSmall =
            base.titleSmall.copy(
                fontWeight = FontWeight.Medium,
            ),
        labelLarge =
            base.labelLarge.copy(fontWeight = FontWeight.Medium),
        bodyLarge =
            base.bodyLarge.copy(
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        bodyMedium =
            base.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 22.sp,
            ),
        bodySmall =
            base.bodySmall.copy(
                fontSize = 12.sp,
                lineHeight = 18.sp,
            ),
    )
}
