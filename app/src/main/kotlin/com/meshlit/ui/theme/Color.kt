package com.meshlit.ui.theme

import androidx.compose.ui.graphics.Color

// Meshlit brand palette — exported from `colors.xml` for Compose.
// Trimmed hex values match the XML resource values exactly.
val MeshlitMidnight = Color(0xFF0A0E1A)
val MeshlitSurface = Color(0xFF121829)
val MeshlitSurfaceVariant = Color(0xFF1B2238)
val MeshlitOutline = Color(0xFF2B3350)

val MeshlitViolet = Color(0xFF7C5CFF)
val MeshlitVioletDim = Color(0xFF4B3FAA)
val MeshlitCyan = Color(0xFF22D3EE)
val MeshlitCyanDim = Color(0xFF1E8E9F)
val MeshlitEmerald = Color(0xFF34D399)
val MeshlitEmeraldDim = Color(0xFF1F7A5B)

val MeshlitTextPrimary = Color(0xFFE6E9F2)
val MeshlitTextSecondary = Color(0xFFA3AAC2)
val MeshlitTextTertiary = Color(0xFF6B7392)

val MeshlitError = Color(0xFFEF4444)
val MeshlitWarning = Color(0xFFF59E0B)
val MeshlitSuccess = Color(0xFF22C55E)

// RunAnywhere-style accent (orange) for the Advanced hub.
val MeshlitAmber = Color(0xFFFF7A1A)
val MeshlitAmberDim = Color(0xFFCC5F0F)

// GPU badge tokens (vendor identification + compute path). These are
// badge-only — never the app accent.
val MeshlitVulkanPurple = Color(0xFFAC10E5)
val MeshlitNvidiaGreen = Color(0xFF76B900)
val MeshlitAmdRed = Color(0xFFED1C24)

// RunAnywhere-style visual tokens. Aliased to existing Meshlit* values
// where possible so there is one source of truth — every Ra* consumer
// reads from these. Mirrors the dark-orange palette of the upstream
// RunAnywhereAndroid sample (sdk/runanywhere-sdks — see plan §External
// reference). Used by RaChip, RaHeroIcon, RaGetButton, RaListCard and
// by BasePalette.RUNANYWHERE in DynamicTheme.kt.
val RaBackground = Color(0xFF0A0A0A)
val RaSurface = Color(0xFF1A1A1A)
val RaSurfaceVariant = Color(0xFF222222)
val RaOutline = Color(0xFF2E2E2E)
val RaTextPrimary = Color(0xFFF5F5F5)
val RaTextSecondary = Color(0xFFB0B0B0)
val RaTextTertiary = Color(0xFF7A7A7A)
val RaOrange = MeshlitAmber          // = Color(0xFFFF7A1A)
val RaOrangeDim = Color(0xFFCC5F0F)
val RaOrangeSoft = Color(0x33FF7A1A) // 20% alpha overlay
val RaGreen = Color(0xFF22C55E)
val RaRed = Color(0xFFEF4444)

// RunAnywhere brand gradient — orange → warm gold. Used by hero
// strips, agent-terminal banner, provider-card accent, and
// anywhere the Meshlit violet→cyan ramp would feel off-brand.
// Mirrors the RunAnywhereAI sample's hero gradient (BrandOrange
// blending into the warm-gold Tertiary40).
val RaGradientStart = Color(0xFFFF6900) // BrandOrange
val RaGradientMid = Color(0xFFFF7B1F)   // Primary70
val RaGradientEnd = Color(0xFFE5C52A)   // Tertiary80 (warm gold)
val RaGradientOnDark = Color(0xFFFFD8A8) // high-contrast text color over the gradient