package com.example.myapplication;

import android.app.PictureInPictureParams;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class VideoPlayerActivity extends AppCompatActivity {
    private WebView webView;
    private GestureDetector gestureDetector;
    private boolean eventConsumed = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        webView = findViewById(R.id.webview);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);


        // Deshabilitar el zoom y los controles de zoom
        webSettings.setSupportZoom(false);  // Deshabilitar el soporte de zoom
        webSettings.setBuiltInZoomControls(false);  // Deshabilitar los controles de zoom integrados
        webSettings.setDisplayZoomControls(false);  // No mostrar los controles de zoom en la interfaz de usuario
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        // Cambiar el User Agent a uno de escritorio
        String desktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36 OPR/60.0.3255.170";
        webSettings.setUserAgentString(desktopUserAgent);

        // Inicializa el detector de gestos
        gestureDetector = new GestureDetector(this, new GestureListener());
        webView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            boolean handled = eventConsumed;
            eventConsumed = false;  // Resetear el indicador después de cada evento
            return handled;
        });







        String videoId = getIntent().getStringExtra("video_url");
        if (videoId != null) {
            loadYouTubeVideoInPiP(videoId);
        } else {
            finish();
        }
    }
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        adjustStatusBarVisibility(newConfig);
    }

    private void adjustStatusBarVisibility(Configuration newConfig) {
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Ocultar la barra de estado
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        } else {
            // Mostrar la barra de estado
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_VISIBLE
            );
        }
    }

    private void loadYouTubeVideoInPiP(String videoId) {
        // Limpiar el ID del video
        String videoId1 = videoId.trim().replaceAll("[^\\w-]", "");

        // Construir el HTML para insertar el video de YouTube en el PiP
        String html = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<style>" +
                "body, html { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; }" +
                "iframe { width: 100%; height: 100%; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<iframe src='https://www.youtube.com/embed/" + videoId1 + "?autoplay=1&controls=1&modestbranding=1' frameborder='0' allowfullscreen></iframe>" +
                "</body>" +
                "</html>";

        // Cargar el HTML en el WebView para el PiP
        webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null);
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_THRESHOLD = 100;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;

        @Override
        public boolean onDown(MotionEvent e) {
            return true; // No consumir el evento aquí, permitir que el WebView lo maneje
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            eventConsumed = true;
            return false;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (Math.abs(e2.getX() - e1.getX()) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                enterPiPMode();
                eventConsumed = true;  // Indicar que este evento específico fue consumido
                return true;
            }
            return false;
        }
    }



    private void enterPiPMode() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Rational aspectRatio = new Rational(20, 9);
            PictureInPictureParams.Builder pipBuilder = new PictureInPictureParams.Builder();
            pipBuilder.setAspectRatio(aspectRatio);
            enterPictureInPictureMode(pipBuilder.build());
        } else {
            Toast.makeText(this, "Picture in Picture mode is not supported on your Android version.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            webView.setVisibility(View.VISIBLE);
        } else {
            webView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
        }
    }
}