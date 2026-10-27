package com.pelican.comictv.ui

/** Placement and directional handoff belong to the theme, independently of route data. */
internal enum class ThemeNavigationPolicy {
    Sidebar, TopBar, BottomDock, ImmersiveMenu;

    companion object {
        fun forPreset(preset: ColorPreset): ThemeNavigationPolicy = when (preset) {
            ColorPreset.Prime -> TopBar
            ColorPreset.Max -> BottomDock
            ColorPreset.Cinema -> ImmersiveMenu
            else -> Sidebar
        }
    }
}
