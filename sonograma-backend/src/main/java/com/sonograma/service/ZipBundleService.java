package com.sonograma.service;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.TrackInfo;
import com.sonograma.dto.VinylPageData;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a ZIP containing:
 *   import.csv
 *   {Artist} - {Album} - {CODE}/
 *     images/{CODE}_front.jpg
 *     audio/{nn}_{TrackName}.mp3
 *     missing.txt               ← only if any download failed
 */
@Slf4j
@Service
public class ZipBundleService {

    private static final String USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final int IMAGE_TIMEOUT_MS = 15_000;
    private static final int MAX_IMAGE_SIZE   = 5 * 1024 * 1024;  // 5 MB
    private static final int MP3_TIMEOUT_MS   = 30_000;
    private static final int MAX_MP3_SIZE     = 20 * 1024 * 1024; // 20 MB
    private static final int DOWNLOAD_THREADS = 10;

    @Value("${sonograma.vinylfuture.timeout-ms:10000}")
    private int timeoutMs;

    private final ExecutorService downloadPool =
        Executors.newFixedThreadPool(DOWNLOAD_THREADS);

    public byte[] buildZip(String csv, Map<InvoiceItem, Optional<VinylPageData>> pageDataMap) throws IOException {

        record TrackResult(String filename, byte[] bytes) {}
        record AlbumResult(InvoiceItem item, VinylPageData page, byte[] imageBytes, List<TrackResult> tracks) {}
        record Mp3Task(int albumIndex, String filename) {}

        // Flatten entries that have page data
        List<Map.Entry<InvoiceItem, VinylPageData>> entries = pageDataMap.entrySet().stream()
            .filter(e -> e.getValue().isPresent())
            .map(e -> Map.entry(e.getKey(), e.getValue().get()))
            .toList();

        // Submit image downloads as flat futures (one per album)
        List<CompletableFuture<byte[]>> imageFutures = entries.stream()
            .map(e -> CompletableFuture.supplyAsync(
                () -> e.getValue().frontImageUrl() != null ? downloadImage(e.getValue().frontImageUrl()) : null,
                downloadPool
            ))
            .toList();

        // Submit MP3 downloads as flat futures (one per track across all albums)
        List<Mp3Task> mp3Tasks = new ArrayList<>();
        List<CompletableFuture<byte[]>> mp3Futures = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            List<TrackInfo> tracks = entries.get(i).getValue().tracks();
            for (int j = 0; j < tracks.size(); j++) {
                TrackInfo track = tracks.get(j);
                if (track.mp3Url() == null || track.mp3Url().isBlank()) continue;
                String trackName = track.name().isBlank() ? track.label() : track.name();
                String filename = String.format("%02d_%s.mp3", j + 1, sanitizePath(trackName));
                mp3Tasks.add(new Mp3Task(i, filename));
                final String url = track.mp3Url();
                mp3Futures.add(CompletableFuture.supplyAsync(() -> downloadMp3(url), downloadPool));
            }
        }

        // Wait for all downloads
        List<byte[]> imageBytes = imageFutures.stream().map(CompletableFuture::join).toList();
        List<byte[]> mp3Bytes   = mp3Futures.stream().map(CompletableFuture::join).toList();

        // Group MP3 results by album index
        Map<Integer, List<TrackResult>> tracksByAlbum = new HashMap<>();
        for (int i = 0; i < mp3Tasks.size(); i++) {
            tracksByAlbum.computeIfAbsent(mp3Tasks.get(i).albumIndex(), k -> new ArrayList<>())
                .add(new TrackResult(mp3Tasks.get(i).filename(), mp3Bytes.get(i)));
        }

        List<AlbumResult> albums = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            albums.add(new AlbumResult(
                entries.get(i).getKey(),
                entries.get(i).getValue(),
                imageBytes.get(i),
                tracksByAlbum.getOrDefault(i, List.of())
            ));
        }

        // Write ZIP
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            addEntry(zos, "import.csv", csv.getBytes(StandardCharsets.UTF_8));

            for (AlbumResult album : albums) {
                String folder = sanitizePath(
                    album.item().artista() + " - " + album.item().album() + " - " + album.item().codigoCatalogo()
                );
                String code = sanitizeCode(album.item().codigoCatalogo());

                List<String> missing = new ArrayList<>();

                // Cover image
                if (album.imageBytes() != null) {
                    String ext = guessExtension(album.page().frontImageUrl(), "jpg");
                    addEntry(zos, folder + "/images/" + code + "_front." + ext, album.imageBytes());
                } else if (album.page().frontImageUrl() != null) {
                    missing.add("front image: " + album.page().frontImageUrl());
                } else {
                    missing.add("front image: not found on product page");
                }

                // MP3 tracks
                for (TrackResult track : album.tracks()) {
                    if (track.bytes() != null) {
                        addEntry(zos, folder + "/audio/" + track.filename(), track.bytes());
                    } else {
                        missing.add("audio: " + track.filename());
                    }
                }

                if (!missing.isEmpty()) {
                    addEntry(zos, folder + "/missing.txt",
                        String.join("\n", missing).getBytes(StandardCharsets.UTF_8));
                }
            }
        }

        return baos.toByteArray();
    }

    private byte[] downloadMp3(String url) {
        try {
            return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(MP3_TIMEOUT_MS)
                .ignoreContentType(true)
                .maxBodySize(MAX_MP3_SIZE)
                .execute()
                .bodyAsBytes();
        } catch (Exception e) {
            log.warn("MP3 download failed '{}': {}", url, e.getMessage());
            return null;
        }
    }

    private byte[] downloadImage(String url) {
        try {
            return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(IMAGE_TIMEOUT_MS)
                .ignoreContentType(true)
                .maxBodySize(MAX_IMAGE_SIZE)
                .execute()
                .bodyAsBytes();
        } catch (Exception e) {
            log.warn("Image download failed '{}': {}", url, e.getMessage());
            return null;
        }
    }

    private void addEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(data);
        zos.closeEntry();
    }

    private String guessExtension(String url, String fallback) {
        if (url == null) return fallback;
        int q = url.indexOf('?');
        String path = q > 0 ? url.substring(0, q) : url;
        int dot = path.lastIndexOf('.');
        if (dot > 0 && dot < path.length() - 1) {
            String ext = path.substring(dot + 1).toLowerCase();
            if (ext.matches("jpg|jpeg|png|gif|webp")) return ext;
        }
        return fallback;
    }

    /** Forward slash included — prevents folder names from being split into ZIP subdirs. */
    private String sanitizePath(String name) {
        return name.replaceAll("[/\\\\:*?\"<>|]", "_").strip();
    }

    private String sanitizeCode(String code) {
        return code.replaceAll("[^A-Za-z0-9\\-]", "_");
    }
}
