package com.antigravity.assetmanager.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colors mapped directly from src/style.css to preserve UI consistency.
 */
object AppColors {
    // Primary & Accent Colors
    val Primary = Color(0xFF4F46E5)       // --primary: #4f46e5
    val PrimaryHover = Color(0xFF4338CA)  // --primary-hover: #4338ca
    val Secondary = Color(0xFF10B981)     // --secondary: #10b981
    val Warning = Color(0xFFF59E0B)       // --warning: #f59e0b
    val Danger = Color(0xFFEF4444)        // --danger: #ef4444

    // Background & Surfaces
    val BgDark = Color(0xFF0F172A)        // --bg-dark: #0f172a
    val BgDarkEnd = Color(0xFF1E1B4B)     // Gradient end: #1e1b4b
    val BgCard = Color(0xB31E293B)        // --bg-card: rgba(30, 41, 59, 0.7)
    val BgCardSolid = Color(0xFF1E293B)   // Solid card background
    val BgInput = Color(0x0DFFFFFF)       // rgba(255, 255, 255, 0.05)

    // Typography
    val TextMain = Color(0xFFF8FAFC)      // --text-main: #f8fafc
    val TextMuted = Color(0xFF94A3B8)     // --text-muted: #94a3b8

    // Glass & Borders
    val BorderGlass = Color(0x1AFFFFFF)   // --border-glass: rgba(255, 255, 255, 0.1)
    val BorderLight = Color(0x33FFFFFF)   // rgba(255, 255, 255, 0.2)
}
