package com.jahirtrap.cconect

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.jahirtrap.cconect.data.Settings

class CConectApp : Application() {
    override fun onCreate() {
        super.onCreate()
        applyLanguage(Settings(this).language)
    }
}

fun applyLanguage(tag: String) {
    val locales =
        if (tag.isBlank()) LocaleListCompat.getEmptyLocaleList()
        else LocaleListCompat.forLanguageTags(tag)
    AppCompatDelegate.setApplicationLocales(locales)
}
