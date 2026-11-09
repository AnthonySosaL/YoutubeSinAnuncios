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
                        // Alto completo: con "ajustar al contenido" la web de Instagram (que usa
                        // height:100%) mide 0 y la pantalla se ve en blanco.
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        // Igual que SnapTube: User-Agent del sistema, almacenamiento web y base de datos
                        // activos, y cookies de terceros (el login de Instagram usa facebook.com).
                        @Suppress("DEPRECATION")
                        settings.databaseEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                val cookies = CookieManager.getInstance().getCookie("https://www.instagram.com").orEmpty()
                                if ("sessionid=" in cookies && !done && url.orEmpty().startsWith("https://www.instagram.com")) {
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
