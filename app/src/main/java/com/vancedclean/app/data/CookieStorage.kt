package com.vancedclean.app.data

import android.content.Context
import android.content.SharedPreferences

object CookieStorage {

    private const val FILE_NAME = "youtube_cookies"
    private const val KEY_COOKIES = "cookies"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun saveCookies(context: Context, cookies: String) {
        val trimmed = cookies.trim()
        if (trimmed.isEmpty()) {
            clearCookies(context)
            return
        }
        getPrefs(context).edit().putString(KEY_COOKIES, trimmed).apply()
    }

    fun getCookies(context: Context): String? {
        return getPrefs(context).getString(KEY_COOKIES, null)?.takeIf { it.isNotBlank() }
    }

    fun clearCookies(context: Context) {
        getPrefs(context).edit().remove(KEY_COOKIES).apply()
    }

    fun isLoggedIn(context: Context): Boolean = !getCookies(context).isNullOrBlank()

    private fun getPrefs(context: Context): SharedPreferences {
        prefs?.let { return it }
        return synchronized(this) {
            prefs ?: context.applicationContext
                .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
                .also { prefs = it }
        }
    }
}
