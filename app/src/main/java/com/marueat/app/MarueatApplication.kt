package com.marueat.app

import android.app.Application
import com.google.android.material.color.DynamicColors

class MarueatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply dynamic colors (system accent color / Material You) to all activities.
        // On Android 12+ the theme colors will follow the user's wallpaper / accent selection.
        // On older devices the static colors defined in colors.xml will be used as fallback.
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}

