package com.sonograma.dto;

public record TrackInfo(String label, String name, String mp3Url, String youtubeUrl) {
    public TrackInfo(String label, String name, String mp3Url) {
        this(label, name, mp3Url, null);
    }
}
