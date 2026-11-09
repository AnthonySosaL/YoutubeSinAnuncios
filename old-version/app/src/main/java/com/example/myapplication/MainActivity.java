package com.example.myapplication;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTubeScopes;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

public class MainActivity extends AppCompatActivity {
    private static final int RC_SIGN_IN = 100;
    private GoogleSignInClient mGoogleSignInClient;
    private YouTube youtubeService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(YouTubeScopes.YOUTUBE_READONLY))
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        findViewById(R.id.sign_in_button).setOnClickListener(view -> signIn());
    }

    private void signIn() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            Log.d("SignInActivity", "Signed in as: " + account.getEmail());
            Toast.makeText(this, "Signed in as: " + account.getDisplayName(), Toast.LENGTH_LONG).show();

            // Inicia SecondActivity pasando la cuenta
            Intent intent = new Intent(MainActivity.this, SecondActivity.class);
            intent.putExtra("account", account);
            startActivity(intent);
            finish(); // Opcional: Llamar a finish() si no quieres que MainActivity esté en la pila de actividades
        } catch (ApiException e) {
            Log.e("SignInActivity", "signInResult:failed code=" + e.getStatusCode() + ", message=" + e.getMessage());
            Toast.makeText(this, "Failed to sign in: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }


    private void setupYouTubeClient(GoogleSignInAccount account) {
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                this, Collections.singleton(YouTubeScopes.YOUTUBE_READONLY));
        credential.setSelectedAccount(account.getAccount());

        youtubeService = new YouTube.Builder(new NetHttpTransport(), new GsonFactory(), credential)
                .setApplicationName(getString(R.string.app_name))
                .build();

        fetchLikedVideos();
    }

    private void fetchLikedVideos() {
        new Thread(() -> {
            try {
                YouTube.Videos.List request = youtubeService.videos().list("snippet,contentDetails,statistics");
                request.setMyRating("like");
                VideoListResponse response = request.execute();

                ArrayList<String> videoTitles = new ArrayList<>();
                ArrayList<String> videoImageUrls = new ArrayList<>();
                ArrayList<String> videoIds = new ArrayList<>();  // Lista para almacenar los IDs de los videos

                for (Video video : response.getItems()) {
                    videoTitles.add(video.getSnippet().getTitle());
                    videoImageUrls.add(video.getSnippet().getThumbnails().getHigh().getUrl());
                    videoIds.add(video.getId());  // Guardar el ID del video
                }

                Intent intent = new Intent(MainActivity.this, SecondActivity.class);
                intent.putStringArrayListExtra("video_titles", videoTitles);
                intent.putStringArrayListExtra("video_image_urls", videoImageUrls);
                intent.putStringArrayListExtra("video_urls", videoIds);  // Pasar los IDs de los videos a la segunda actividad
                startActivity(intent);

            } catch (IOException e) {
                Log.e("YouTube", "Error fetching liked videos", e);
            }
        }).start();
    }


}
