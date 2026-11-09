package com.vancedclean.app

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.vancedclean.app.data.AppPreferences
import com.vancedclean.app.data.CurrentSettings
import com.vancedclean.app.data.NewPipeSetup
import com.vancedclean.app.navigation.DeepLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.vancedclean.app.ui.VideoPlayerSheet
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vancedclean.app.navigation.NavGraph
import com.vancedclean.app.navigation.Screen
import com.vancedclean.app.player.PlayerSheet
import com.vancedclean.app.player.VideoPlayerManager
import com.vancedclean.app.ui.theme.VancedCleanTheme

class MainActivity : ComponentActivity() {

    private var navController: NavHostController? = null
    private var pendingIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingIntent = intent
        enableEdgeToEdge()
        requestNotificationPermission()
        observeSettings()
        // Última pulsación de Atrás (nada más que cerrar): termina el video y cierra la app de verdad.
        // Android 12+ solo la mandaría al fondo y el video seguiría ahí al volver.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                PlayerSheet.close()
                finish()
            }
        })
        setContent {
            VancedCleanTheme {
                val controller = rememberNavController()

                DisposableEffect(controller) {
                    navController = controller
                    pendingIntent?.let { DeepLinks.handle(it, controller) }
                    pendingIntent = null
                    onDispose { navController = null }
                }

                Box(Modifier.fillMaxSize()) {
                    NavGraph(navController = controller)
                    // El reproductor flota encima de todas las pantallas.
                    VideoPlayerSheet(navController = controller)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val controller = navController
        if (controller != null) DeepLinks.handle(intent, controller) else pendingIntent = intent
    }

    override fun onStop() {
        super.onStop()
        // Ajustes → "Reproducción en segundo plano" desactivado: pausa al salir (salvo en PiP).
        val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode
        if (!CurrentSettings.value.backgroundPlayback && !inPip && !isChangingConfigurations) {
            VideoPlayerManager.pause()
        }
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            AppPreferences.getInstance(this@MainActivity).settings.collect { settings ->
                CurrentSettings.value = settings
                launch(Dispatchers.IO) { NewPipeSetup.ensure() }
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PlayerSheet.setInPip(isInPictureInPictureMode)
        // Si se cierra la ventanita con la X, la actividad queda detenida: paramos el video.
        if (!isInPictureInPictureMode && lifecycle.currentState == androidx.lifecycle.Lifecycle.State.CREATED) {
            PlayerSheet.close()
        }
    }

    override fun onDestroy() {
        // Cerrar la app (atrás o deslizarla en recientes) termina el video: al volver se abre en Inicio.
        if (isFinishing && !isChangingConfigurations) PlayerSheet.close()
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (shouldEnterPictureInPicture()) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    private fun shouldEnterPictureInPicture(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            VideoPlayerManager.isPlaying.value &&
            CurrentSettings.value.backgroundPlayback
}
