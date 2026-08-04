package com.android.requi.preferences

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "bcr_gui_preferences"
        private const val KEY_FOLDER_URI = "folder_uri"
        private const val KEY_TEMPLATE = "filename_template"
        private const val KEY_EXTENSION = "file_extension"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_AMOLED_MODE = "amoled_mode"

        const val DEFAULT_TEMPLATE = "{date}[_{direction}|][_sim{sim_slot}|][_{phone_number}|][_[{contact_name}|{caller_name}|{call_log_name}]|]"
        const val DEFAULT_EXTENSION = "all"
        const val DEFAULT_ACCENT_COLOR = "purple"
    }

    var folderUri: String?
        get() = prefs.getString(KEY_FOLDER_URI, null)
        set(value) = prefs.edit().putString(KEY_FOLDER_URI, value).apply()

    var filenameTemplate: String
        get() = prefs.getString(KEY_TEMPLATE, DEFAULT_TEMPLATE) ?: DEFAULT_TEMPLATE
        set(value) = prefs.edit().putString(KEY_TEMPLATE, value).apply()

    var fileExtension: String
        get() = prefs.getString(KEY_EXTENSION, DEFAULT_EXTENSION) ?: DEFAULT_EXTENSION
        set(value) = prefs.edit().putString(KEY_EXTENSION, value).apply()

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    var accentColor: String
        get() = prefs.getString(KEY_ACCENT_COLOR, DEFAULT_ACCENT_COLOR) ?: DEFAULT_ACCENT_COLOR
        set(value) = prefs.edit().putString(KEY_ACCENT_COLOR, value).apply()

    var amoledMode: Boolean
        get() = prefs.getBoolean(KEY_AMOLED_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_AMOLED_MODE, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
