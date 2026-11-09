package com.example.myapplication;

public class Video {
    private String title;
    private String thumbnailURL;

    public Video(String title, String thumbnailURL) {
        this.title = title;
        this.thumbnailURL = thumbnailURL;
    }

    public String getTitle() {
        return title;
    }

    public String getThumbnailURL() {
        return thumbnailURL;
    }
}


