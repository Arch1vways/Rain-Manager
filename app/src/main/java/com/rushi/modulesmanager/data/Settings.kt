package com.rushi.modulesmanager.data

import android.app.LocaleManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.LocaleList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class AppIconOption(val key: String, val label: String, val aliasClass: String) {
    DEFAULT("default", "Default", "com.rushi.modulesmanager.MainActivityDefault"),
    DEFAULT_PLUS("default_plus", "Default+", "com.rushi.modulesmanager.MainActivityDefaultPlus"),
    CARDBOARD("cardboard", "Cardboard Box", "com.rushi.modulesmanager.MainActivityCardboard"),
    SIMPLE("simple", "Simple", "com.rushi.modulesmanager.MainActivitySimple");

    companion object {
        fun fromKey(key: String?): AppIconOption = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

enum class AppLanguageOption(val key: String, val label: String, val tag: String?) {
    SYSTEM("system", "System", null),
    ENGLISH("en", "English", "en"),
    RUSSIAN("ru", "Русский", "ru");

    companion object {
        fun fromKey(key: String?): AppLanguageOption = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("modules_manager", Context.MODE_PRIVATE)
    private val translationPrefs = context.getSharedPreferences("translation_cache", Context.MODE_PRIVATE)

    private val _channel = MutableStateFlow(UpdateChannel.fromKey(prefs.getString(KEY_CHANNEL, null)))
    val channel: StateFlow<UpdateChannel> = _channel.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.fromKey(prefs.getString(KEY_SORT, null)))
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val _installer = MutableStateFlow(InstallerType.fromKey(prefs.getString(KEY_INSTALLER, null)))
    val installer: StateFlow<InstallerType> = _installer.asStateFlow()

    private val _theme = MutableStateFlow(AppTheme.fromKey(prefs.getString(KEY_THEME, null)))
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    private val _amoled = MutableStateFlow(prefs.getBoolean(KEY_AMOLED, false))
    val amoled: StateFlow<Boolean> = _amoled.asStateFlow()

    private val _systemColors = MutableStateFlow(prefs.getBoolean(KEY_SYSTEM_COLORS, true))
    val systemColors: StateFlow<Boolean> = _systemColors.asStateFlow()

    private val _updatesFirst = MutableStateFlow(prefs.getBoolean(KEY_UPDATES_FIRST, false))
    val updatesFirst: StateFlow<Boolean> = _updatesFirst.asStateFlow()

    private val _autoTranslate = MutableStateFlow(prefs.getBoolean(KEY_AUTO_TRANSLATE, false))
    val autoTranslate: StateFlow<Boolean> = _autoTranslate.asStateFlow()

    private val _concurrentDownloads = MutableStateFlow(
        prefs.getInt(KEY_CONCURRENT_DOWNLOADS, DownloadSlots.DEFAULT_LIMIT)
            .coerceIn(DownloadSlots.MIN_LIMIT, DownloadSlots.MAX_LIMIT),
    )
    val concurrentDownloads: StateFlow<Int> = _concurrentDownloads.asStateFlow()

    /** Null means the device language. */
    private val _translateLanguage = MutableStateFlow(
        prefs.getString(KEY_TRANSLATE_LANGUAGE, null)?.takeIf { it.isNotBlank() },
    )
    val translateLanguage: StateFlow<String?> = _translateLanguage.asStateFlow()

    private val _appIcon = MutableStateFlow(AppIconOption.fromKey(prefs.getString(KEY_APP_ICON, null)))
    val appIcon: StateFlow<AppIconOption> = _appIcon.asStateFlow()

    private val _appLanguage = MutableStateFlow(AppLanguageOption.fromKey(prefs.getString(KEY_APP_LANGUAGE, null)))
    val appLanguage: StateFlow<AppLanguageOption> = _appLanguage.asStateFlow()

    fun setChannel(channel: UpdateChannel) {
        prefs.edit().putString(KEY_CHANNEL, channel.key).apply()
        _channel.value = channel
    }

    fun setSortMode(mode: SortMode) {
        prefs.edit().putString(KEY_SORT, mode.key).apply()
        _sortMode.value = mode
    }

    fun setInstaller(installer: InstallerType) {
        prefs.edit().putString(KEY_INSTALLER, installer.key).apply()
        _installer.value = installer
    }

    fun setUpdatesFirst(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_UPDATES_FIRST, enabled).apply()
        _updatesFirst.value = enabled
    }

    fun setAutoTranslate(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_TRANSLATE, enabled).apply()
        _autoTranslate.value = enabled
    }

    fun setConcurrentDownloads(count: Int) {
        val clamped = count.coerceIn(DownloadSlots.MIN_LIMIT, DownloadSlots.MAX_LIMIT)
        prefs.edit().putInt(KEY_CONCURRENT_DOWNLOADS, clamped).apply()
        _concurrentDownloads.value = clamped
    }

    fun setTranslateLanguage(language: String?) {
        prefs.edit().putString(KEY_TRANSLATE_LANGUAGE, language).apply()
        _translateLanguage.value = language
    }

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_THEME, theme.key).apply()
        _theme.value = theme
    }

    fun setAmoled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AMOLED, enabled).apply()
        _amoled.value = enabled
    }

    fun setSystemColors(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SYSTEM_COLORS, enabled).apply()
        _systemColors.value = enabled
    }

    fun setAppIcon(context: Context, option: AppIconOption) {
        prefs.edit().putString(KEY_APP_ICON, option.key).apply()
        _appIcon.value = option
        val pm = context.packageManager
        AppIconOption.entries.forEach { entry ->
            val state = if (entry == option) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(
                ComponentName(context, entry.aliasClass),
                state,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    fun setAppLanguage(context: Context, option: AppLanguageOption) {
        prefs.edit().putString(KEY_APP_LANGUAGE, option.key).apply()
        _appLanguage.value = option
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val lm = context.getSystemService(LocaleManager::class.java)
            lm?.applicationLocales = if (option.tag != null) {
                LocaleList.forLanguageTags(option.tag)
            } else {
                LocaleList.getEmptyLocaleList()
            }
        } else {
            val locale = option.tag?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()
            Locale.setDefault(locale)
        }
    }

    /** Chinese ROMs only: whether the app list permission has already been requested once. */
    fun installedAppsPermissionAsked(): Boolean =
        prefs.getBoolean(KEY_INSTALLED_APPS_ASKED, false)

    fun markInstalledAppsPermissionAsked() {
        prefs.edit().putBoolean(KEY_INSTALLED_APPS_ASKED, true).apply()
    }

    fun lastLiveCheck(): Long = prefs.getLong(KEY_LAST_LIVE_CHECK, 0L)

    fun setLastLiveCheck(time: Long) {
        prefs.edit().putLong(KEY_LAST_LIVE_CHECK, time).apply()
    }

    fun cachedTranslation(key: String): String? = translationPrefs.getString(key, null)

    fun saveTranslation(key: String, text: String) {
        translationPrefs.edit().putString(key, text).apply()
    }

    private companion object {
        const val KEY_CHANNEL = "update_channel"
        const val KEY_SORT = "sort_mode"
        const val KEY_INSTALLER = "installer_type"
        const val KEY_THEME = "app_theme"
        const val KEY_AMOLED = "amoled_mode"
        const val KEY_SYSTEM_COLORS = "system_colors"
        const val KEY_UPDATES_FIRST = "updates_first"
        const val KEY_AUTO_TRANSLATE = "auto_translate"
        const val KEY_CONCURRENT_DOWNLOADS = "concurrent_downloads"
        const val KEY_TRANSLATE_LANGUAGE = "translate_language"
        const val KEY_LAST_LIVE_CHECK = "last_live_check"
        const val KEY_INSTALLED_APPS_ASKED = "installed_apps_asked"
        const val KEY_APP_ICON = "app_icon"
        const val KEY_APP_LANGUAGE = "app_language"
    }
}
