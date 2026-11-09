package com.vancedclean.app.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.vancedclean.app.download.InstagramSession
import com.vancedclean.app.ui.components.toast

/**
 * Inicio de sesión en Instagram con su web oficial (la app nunca ve la contraseña):
 * al entrar, se guardan sus cookies para descargar reels y posts que piden sesión.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramLoginScreen(navController: NavController) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Iniciar sesión en Instagram") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    var done = false
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        // Instagram no dibuja su web si detecta el navegador interno ("; wv"):
                        // usamos el User-Agent de Chrome móvil y aceptamos sus cookies de terceros.
                        settings.userAgentString = android.webkit.WebSettings.getDefaultUserAgent(ctx)
                            .replace("; wv", "").replace(Regex("""Version/\d+\.\d+ """), "")
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                val cookies = CookieManager.getInstance().getCookie("https://www.instagram.com").orEmpty()
                                if ("sessionid=" in cookies && !done) {
                                    done = true
                                    InstagramSession.save(context, cookies)
                                    toast(context, "Sesión de Instagram iniciada")
                                    navController.popBackStack()
                                }
                            }
                        }
                        loadUrl("https://www.instagram.com/accounts/login/")
                    }
                },
                onRelease = { it.destroy() }
            )
        }
    }
}
