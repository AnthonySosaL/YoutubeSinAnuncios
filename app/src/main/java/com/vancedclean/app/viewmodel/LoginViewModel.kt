package com.vancedclean.app.viewmodel

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.lifecycle.ViewModel
import com.vancedclean.app.data.CookieStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONTokener

private val AUTH_COOKIE_NAMES = setOf("SID", "SAPISID", "__Secure-1PSID", "__Secure-3PSID")

/**
 * Indica si la cadena de cookies ("A=1; B=2; ...") contiene cookies de sesión de Google.
 * Sirve para distinguir un login real de una simple visita anónima a youtube.com.
 */
fun hasAuthCookies(cookies: String?): Boolean {
    if (cookies.isNullOrBlank()) return false
    val names = cookies.split(';').map { it.substringBefore('=').trim() }
    return names.any { it in AUTH_COOKIE_NAMES }
}

/**
 * Gestiona el estado de sesión de la app.
 *
 * El estado es global (compartido entre instancias) y las operaciones de E/S corren en un
 * scope de aplicación, de modo que guardar las cookies o cargar el perfil no se cancela
 * cuando LoginScreen se cierra y su ViewModel se destruye.
 *
 * Seguridad: las cookies solo se guardan en CookieStorage (EncryptedSharedPreferences),
 * y DownloaderImpl solo las envía a dominios de Google/YouTube.
 */
class LoginViewModel : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()
    val userName: StateFlow<String> = _userName.asStateFlow()
    val userAvatar: StateFlow<String> = _userAvatar.asStateFlow()

    /** Comprueba si hay cookies guardadas y, de ser así, las aplica al DownloaderImpl. */
    fun checkLoginStatus(context: Context) {
        val appContext = context.applicationContext
        appScope.launch {
            val cookies = CookieStorage.getCookies(appContext)
            if (hasAuthCookies(cookies)) {
                DownloaderImpl.getInstance().setCookies(cookies)
                _isLoggedIn.value = true
                if (_userName.value.isEmpty()) {
                    loadUserInfo()
                }
            } else {
                DownloaderImpl.getInstance().setCookies(null)
                _isLoggedIn.value = false
                _userName.value = ""
                _userAvatar.value = ""
            }
        }
    }

    /** Guarda las cookies capturadas del WebView, las inyecta en el DownloaderImpl y carga el perfil. */
    fun onLoginSuccess(context: Context, cookies: String) {
        if (!hasAuthCookies(cookies)) return
        val appContext = context.applicationContext

        // Disponibles de inmediato para las siguientes peticiones de NewPipe.
        DownloaderImpl.getInstance().setCookies(cookies)
        _isLoggedIn.value = true

        appScope.launch {
            CookieStorage.saveCookies(appContext, cookies)
        }
        loadUserInfo()
    }

    /** Borra las cookies guardadas, limpia el DownloaderImpl y la sesión del WebView. */
    fun logout(context: Context) {
        val appContext = context.applicationContext

        userInfoJob?.cancel()
        userInfoJob = null

        DownloaderImpl.getInstance().setCookies(null)
        _isLoggedIn.value = false
        _userName.value = ""
        _userAvatar.value = ""

        // Elimina la sesión de Google del WebView para que el siguiente login sea limpio.
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
            WebStorage.getInstance().deleteAllData()
        } catch (e: Exception) {
            // WebView no disponible: nada que limpiar.
        }

        appScope.launch {
            CookieStorage.clearCookies(appContext)
        }
    }

    /**
     * Obtiene nombre y avatar de la cuenta con una petición a youtube.com usando las cookies
     * de sesión (el DownloaderImpl las añade automáticamente).
     * Si falla, se muestra un nombre genérico; el login sigue siendo válido.
     */
    fun loadUserInfo() {
        if (!_isLoggedIn.value) return

        userInfoJob?.cancel()
        userInfoJob = appScope.launch {
            val info = try {
                fetchAccountInfo()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }

            if (!_isLoggedIn.value) return@launch

            if (info != null) {
                _userName.value = info.first.ifBlank { DEFAULT_USER_NAME }
                _userAvatar.value = info.second
            } else if (_userName.value.isEmpty()) {
                _userName.value = DEFAULT_USER_NAME
            }
        }
    }

    private fun fetchAccountInfo(): Pair<String, String>? {
        val headers = mapOf(
            "User-Agent" to listOf(WEB_USER_AGENT),
            "Accept-Language" to listOf("es-ES,es;q=0.9,en;q=0.8")
        )
        val response = DownloaderImpl.getInstance().get(ACCOUNT_ENDPOINT, headers)
        if (response.responseCode() !in 200..299) return null

        val body = response.responseBody()
        if (body.isBlank()) return null

        val name = NAME_REGEX.find(body)?.groupValues?.getOrNull(1)?.let { unescapeJson(it) }.orEmpty()
        var avatar = AVATAR_REGEX.find(body)?.groupValues?.getOrNull(1)?.let { unescapeJson(it) }.orEmpty()
        if (avatar.startsWith("//")) avatar = "https:$avatar"

        if (name.isBlank() && avatar.isBlank()) return null
        return name to avatar
    }

    private fun unescapeJson(raw: String): String =
        try {
            (JSONTokener(QUOTE + raw + QUOTE).nextValue() as? String) ?: raw
        } catch (e: Exception) {
            raw
        }

    private companion object {
        const val ACCOUNT_ENDPOINT = "https://www.youtube.com/getAccountSwitcherEndpoint"
        const val DEFAULT_USER_NAME = "Usuario"
        const val QUOTE = "\""
        const val WEB_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"

        // \x22 = comillas dobles dentro de la expresión regular.
        val NAME_REGEX = Regex("""\x22accountName\x22\s*:\s*\{\s*\x22simpleText\x22\s*:\s*\x22((?:[^\x22\\]|\\.)*)""")
        val AVATAR_REGEX = Regex("""\x22accountPhoto\x22\s*:\s*\{\s*\x22thumbnails\x22\s*:\s*\[\s*\{\s*\x22url\x22\s*:\s*\x22((?:[^\x22\\]|\\.)*)""")

        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val _isLoggedIn = MutableStateFlow(false)
        private val _userName = MutableStateFlow("")
        private val _userAvatar = MutableStateFlow("")

        @Volatile
        var userInfoJob: Job? = null
    }
}
