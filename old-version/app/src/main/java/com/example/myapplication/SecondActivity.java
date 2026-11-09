package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.material.navigation.NavigationView;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTubeRequestInitializer;
import com.google.api.services.youtube.YouTubeScopes;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelContentDetails;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.PlaylistListResponse;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SecondActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private VideoAdapter videoAdapter;
    private EditText searchEditText;
    private ImageButton searchButton, closeSearchButton;
    private YouTube youtubeService;
    private DrawerLayout drawerLayout;
    private String nextPageToken = null;
    private final int RESULTS_PER_PAGE = 10;

    private boolean isLoading = false;
    private boolean isLastPage = false;
    private int currentPage = 1;
    private boolean inLikedVideos = false;
    private boolean inHistoryVideos = false;
    private boolean inPruebaVideos = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        drawerLayout = findViewById(R.id.drawer_layout);

        ImageButton menuButton = findViewById(R.id.menuButton);
        menuButton.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        videoAdapter = new VideoAdapter(this, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        recyclerView.setAdapter(videoAdapter);

        searchEditText = findViewById(R.id.searchEditText);
        searchButton = findViewById(R.id.searchButton);
        closeSearchButton = findViewById(R.id.closeSearchButton);

        searchButton.setOnClickListener(v -> {
            if (searchEditText.getVisibility() == View.GONE) {
                searchEditText.setVisibility(View.VISIBLE);
                closeSearchButton.setVisibility(View.VISIBLE);
                searchEditText.requestFocus();
                openKeyboard(searchEditText);  // Abrir el teclado
            } else {
                performSearch(searchEditText.getText().toString());
            }
        });




        closeSearchButton.setOnClickListener(v -> {
            searchEditText.clearFocus(); // Quita el foco del EditText
            hideKeyboard();  // Luego oculta el teclado
            searchEditText.setVisibility(View.GONE);
            closeSearchButton.setVisibility(View.GONE);
            searchEditText.setText("");
        });


        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            inLikedVideos = id == R.id.nav_like;
            inHistoryVideos = id == R.id.nav_history;
            inPruebaVideos = id == R.id.nav_prueba;
            if (inLikedVideos) {
                fetchLikedVideos();
            } else if (inHistoryVideos) {
                fetchHistoryVideos();
            }
            else if (inPruebaVideos) {
                fetchPlaylists();  // Carga más videos si no es la última página y está en la vista de vídeos de "Historial"
                Toast.makeText(this, "Error fetching liked videos.", Toast.LENGTH_SHORT).show();

            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });




        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(searchEditText.getText().toString());
                searchEditText.clearFocus(); // Quita el foco
                hideKeyboard(); // Asegúrate de llamarlo aquí
                return true;
            }
            return false;
        });



        videoAdapter.setOnItemClickListener(videoUrl -> {
            Intent intent = new Intent(SecondActivity.this, VideoPlayerActivity.class);
            intent.putExtra("video_url", videoUrl);
            startActivity(intent);
        });

        initializeYouTubeService();

        if (savedInstanceState != null) {
            ArrayList<String> videoTitles = savedInstanceState.getStringArrayList("videoTitles");
            ArrayList<String> videoImageUrls = savedInstanceState.getStringArrayList("videoImageUrls");
            ArrayList<String> videoIds = savedInstanceState.getStringArrayList("videoIds");
            if (videoTitles != null && videoImageUrls != null && videoIds != null) {
                videoAdapter.updateData(videoTitles, videoImageUrls, videoIds);
            }
        }
        GoogleSignInAccount account = getIntent().getParcelableExtra("account");
        if (account != null) {
            setupYouTubeClient(account);
        }
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition();

                if (!isLoading && !isLastPage && (lastVisibleItemPosition + 1) == totalItemCount) {
                    if (inLikedVideos) {
                        fetchLikedVideos();  // Carga más videos si no es la última página y está en la vista de vídeos "Liked"
                    } else if (inHistoryVideos) {
                        fetchHistoryVideos();  // Carga más videos si no es la última página y está en la vista de vídeos de "Historial"

                    }
                }
            }
        });






    }


    private void fetchHistoryVideos() {
        if (youtubeService != null && !isLoading) {
            isLoading = true;

            new Thread(() -> {
                try {
                    // Solicitar los detalles del canal autenticado, incluyendo ID y listas de reproducción relacionadas
                    YouTube.Channels.List channelRequest = youtubeService.channels().list("id, contentDetails");
                    channelRequest.setMine(true); // Indica que queremos el canal del usuario autenticado

                    // Ejecutar la solicitud y obtener la respuesta
                    ChannelListResponse channelResponse = channelRequest.execute();

                    // Verificar si se obtuvo un canal y extraer detalles
                    if (channelResponse.getItems() != null && !channelResponse.getItems().isEmpty()) {
                        Channel channel = channelResponse.getItems().get(0);
                        String channelId = channel.getId(); // Obtener el ID del canal
                        Log.d("YouTubeHistory", "Channel ID: " + channelId);

                        String historyPlaylistId = channel.getContentDetails().getRelatedPlaylists().getWatchHistory(); // Obtener ID de la lista de reproducción de historial
                        Log.d("YouTubeHistory", "Watch History Playlist ID: " + historyPlaylistId);

                        // Usar el ID de la lista de reproducción para obtener los videos
                    } else {
                        Log.d("YouTubeHistory", "No channel found for the authenticated user.");
                    }
                } catch (IOException e) {
                    Log.e("YouTubeHistory", "Error fetching channel content details", e);
                }
            }).start();
        }
    }

    private void fetchPlaylists() {
        if (youtubeService != null && !isLoading) {
            isLoading = true;

            new Thread(() -> {
                try {
                    // Llama al método para obtener el canal del usuario autenticado
                    YouTube.Channels.List channelsList = youtubeService.channels().list("snippet,contentDetails,statistics");
                    channelsList.setMine(true);
                    channelsList.setFields("items(id)");
                    ChannelListResponse response = channelsList.execute();

                    // Verifica que la respuesta tiene al menos un canal
                    if (!response.getItems().isEmpty()) {
                        // Obtiene el channelId del primer canal en la lista
                        String channelId = response.getItems().get(0).getId();

                        // Ahora que tienes el channelId, puedes obtener las listas de reproducción
                        YouTube.Playlists.List playlistsList = youtubeService.playlists().list("snippet,contentDetails");
                        Log.e("channelId", "channelId:  " + channelId);
                        playlistsList.setChannelId(channelId);
                        playlistsList.setFields("items(id,snippet/title,snippet/description)");
                        PlaylistListResponse playlistResponse = playlistsList.execute();

                        // Procesa las listas de reproducción obtenidas
                        for (Playlist playlist : playlistResponse.getItems()) {
                            System.out.println("Playlist: " + playlist.getSnippet().getTitle());
                        }
                    } else {
                        System.out.println("No channel found for the authenticated user.");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    isLoading = false;
                }
            }).start();
        }
    }





    private void fetchLikedVideos() {
        if (youtubeService != null && !isLoading) {
            isLoading = true; // Asegúrate de que no se realizan múltiples cargas al mismo tiempo
            new Thread(() -> {
                try {
                    YouTube.Videos.List request = youtubeService.videos().list("snippet,contentDetails,statistics");
                    request.setMyRating("like");
                    request.setMaxResults(10L); // Carga 10 resultados por vez
                    if (nextPageToken != null) {
                        request.setPageToken(nextPageToken);
                    }

                    VideoListResponse response = request.execute();
                    nextPageToken = response.getNextPageToken(); // Guarda el próximo token para la siguiente carga

                    List<String> videoTitles = new ArrayList<>();
                    List<String> videoImageUrls = new ArrayList<>();
                    List<String> videoIds = new ArrayList<>();

                    for (Video video : response.getItems()) {
                        videoTitles.add(video.getSnippet().getTitle());
                        videoImageUrls.add(video.getSnippet().getThumbnails().getHigh().getUrl());
                        videoIds.add(video.getId());
                    }

                    runOnUiThread(() -> {
                        if (videoTitles.size() > 0) {
                            videoAdapter.addData(videoTitles, videoImageUrls, videoIds); // Agrega nuevos datos al adaptador
                        }
                        isLoading = false; // Restablece la bandera de carga
                        if (nextPageToken == null) {
                            isLastPage = true; // Marca si es la última página
                        }
                    });
                } catch (Exception e) {
                    Log.e("YouTube", "Error fetching liked videos", e);
                    runOnUiThread(() -> {
                        isLoading = false;
                    });
                }
            }).start();
        }
    }


    private void setupYouTubeClient(GoogleSignInAccount account) {
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                this, Collections.singleton(YouTubeScopes.YOUTUBE_READONLY));
        credential.setSelectedAccount(account.getAccount());

        youtubeService = new YouTube.Builder(new NetHttpTransport(), new JacksonFactory(), credential)
                .setApplicationName(getString(R.string.app_name))
                .build();
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view == null) {
            view = getWindow().getDecorView(); // Obtener la vista decoradora de la ventana
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
    private void openKeyboard(View view) {
        if (view.requestFocus()) {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }





    private void performSearch(String query) {
        if (query.isEmpty()) {
            Toast.makeText(this, "Please enter a search query.", Toast.LENGTH_SHORT).show();
            return;
        } else {
            // Lógica de búsqueda
            hideKeyboard();  // Ocultar el teclado después de iniciar la búsqueda
        }

        new Thread(() -> {
            try {
                YouTube.Search.List search = youtubeService.search().list("snippet");
                search.setQ(query);
                search.setType("video");
                search.setMaxResults(25L);

                SearchListResponse searchResponse = search.execute();
                List<String> videoTitles = new ArrayList<>();
                List<String> videoImageUrls = new ArrayList<>();
                List<String> videoIds = new ArrayList<>();

                for (SearchResult result : searchResponse.getItems()) {
                    videoTitles.add(result.getSnippet().getTitle());
                    videoImageUrls.add(result.getSnippet().getThumbnails().getHigh().getUrl());
                    videoIds.add(result.getId().getVideoId());
                }

                runOnUiThread(() -> videoAdapter.updateData(videoTitles, videoImageUrls, videoIds));
            } catch (IOException e) {
                Log.e("SecondActivity", "Error fetching videos based on query", e);
                runOnUiThread(() -> Toast.makeText(this, "Error fetching videos.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList("videoTitles", new ArrayList<>(videoAdapter.getTitles()));
        outState.putStringArrayList("videoImageUrls", new ArrayList<>(videoAdapter.getVideoImageUrls()));
        outState.putStringArrayList("videoIds", new ArrayList<>(videoAdapter.getVideoIds()));
    }

    private void initializeYouTubeService() {
        youtubeService = new YouTube.Builder(new NetHttpTransport(), new JacksonFactory(), request -> {})
                .setApplicationName(getString(R.string.app_name))
                .setYouTubeRequestInitializer(new YouTubeRequestInitializer(getString(R.string.api_key)))
                .build();
    }
}
