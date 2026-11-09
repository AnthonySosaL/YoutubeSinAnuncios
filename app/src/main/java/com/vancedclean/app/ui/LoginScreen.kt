package com.vancedclean.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vancedclean.app.viewmodel.LoginViewModel
import com.vancedclean.app.viewmodel.hasAuthCookies

private const val INTERACTIVE_LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube&continue=https://www.youtube.com/"

// passive=true: si ya hay sesión de Google en el WebView redirige directo a YouTube;
// si no la hay, vuelve a YouTube sin sesión y entonces se carga el login interactivo.
private const val PASSIVE_LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube&passive=true&continue=https://www.youtube.com/"

private const val YOUTUBE_URL = "https://www.youtube.com/"

private const val WEBVIEW_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"

private val YOUTUBE_HOSTS = setOf("www.youtube.com", "m.youtube.com", "youtube.com")

private class LoginFlowState {
    var handled = false
    var interactiveLoaded = false
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: LoginViewModel = viewModel()
) {
    val context = LocalContext.current
    val flow = remember { LoginFlowState() }
    var isLoading by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        val current = webView
        if (current != null && current.canGoBack()) {
            current.goBack()
        } else {
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Iniciar sesión con Google") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = WEBVIEW_USER_AGENT
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.setSupportMultipleWindows(false)
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        // Se tiene en cuenta la verificación SSL por defecto: no se
                        // sobrescribe onReceivedSslError, así que los errores cancelan la carga.
                        webViewClient = object : WebViewClient() {

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean {
                                tryFinishLogin(request.url.toString(), flow) { cookies ->
                                    viewModel.onLoginSuccess(context, cookies)
                                    navController.popBackStack()
                                }
                                return false
                            }

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                isLoading = false
                                if (url == null || flow.handled) return

                                val finished = tryFinishLogin(url, flow) { cookies ->
                                    viewModel.onLoginSuccess(context, cookies)
                                    navController.popBackStack()
                                }

                                // Llegamos a YouTube sin sesión (login pasivo sin cuenta):
                                // se muestra el login interactivo una sola vez.
                                if (!finished && isYoutubeHost(url) && !flow.interactiveLoaded) {
                                    flow.interactiveLoaded = true
                                    view.loadUrl(INTERACTIVE_LOGIN_URL)
                                }
                            }
                        }

                        loadUrl(PASSIVE_LOGIN_URL)
                        webView = this
                    }
                },
                onRelease = { view ->
                    view.stopLoading()
                    view.webViewClient = WebViewClient()
                    view.destroy()
                    webView = null
                }
            )

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }
        }
    }
}

private fun isYoutubeHost(url: String): Boolean {
    val uri = Uri.parse(url)
    if (uri.scheme != "https") return false
    val host = uri.host?.lowercase() ?: return false
    return host in YOUTUBE_HOSTS
}

/**
 * Si la URL es de youtube.com y el WebView ya tiene cookies de sesión de Google,
 * extrae las cookies, las entrega a [onSuccess] (una sola vez) y devuelve true.
 */
private fun tryFinishLogin(
    url: String,
    flow: LoginFlowState,
    onSuccess: (String) -> Unit
): Boolean {
    if (flow.handled) return true
    if (!isYoutubeHost(url)) return false

    val cookies = CookieManager.getInstance().getCookie(url)
        ?: CookieManager.getInstance().getCookie(YOUTUBE_URL)

    if (!hasAuthCookies(cookies)) return false

    flow.handled = true
    CookieManager.getInstance().flush()
    onSuccess(cookies.orEmpty())
    return true
}
