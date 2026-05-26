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
 *     images/{CODE}_front.jpg   ← downloaded in parallel (small, ~7 KB)
 *     playlist.m3u              ← direct MP3 URLs, playable in VLC/iTunes
 *     missing.txt               ← only if image download failed
 *
 * MP3 files are NOT downloaded: each album can have ~8 tracks × 4.5 MB ≈ 36 MB
 * per album, which is impractical to bulk-download in a single HTTP request.
 * The playlist.m3u lets the user stream or batch-download tracks at their own pace.
 */
@Slf4j
@Service
public class ZipBundleService {

    private static final String USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final int IMAGE_TIMEOUT_MS = 15_000;
    private static final int MAX_IMAGE_SIZE   = 5 * 1024 * 1024; // 5 MB
    private static final int DOWNLOAD_THREADS = 10;

    @Value("${sonograma.vinylfuture.timeout-ms:10000}")
    private int timeoutMs;

    private final ExecutorService downloadPool =
        Executors.newFixedThreadPool(DOWNLOAD_THREADS);

    public byte[] buildZip(String csv, Map<InvoiceItem, Optional<VinylPageData>> pageDataMap) throws IOException {

        // Download all cover images in parallel
        record AlbumData(InvoiceItem item, VinylPageData page, byte[] imageBytes) {}

        List<CompletableFuture<AlbumData>> futures = new ArrayList<>();
        for (Map.Entry<InvoiceItem, Optional<VinylPageData>> entry : pageDataMap.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            InvoiceItem item = entry.getKey();
            VinylPageData page = entry.getValue().get();

            CompletableFuture<AlbumData> f = CompletableFuture.supplyAsync(() -> {
                byte[] img = page.frontImageUrl() != null ? downloadImage(page.frontImageUrl()) : null;
                return new AlbumData(item, page, img);
            }, downloadPool);
            futures.add(f);
        }

        List<AlbumData> albums = futures.stream()
            .map(CompletableFuture::join)
            .toList();

        // Write ZIP
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            addEntry(zos, "import.csv", csv.getBytes(StandardCharsets.UTF_8));

            for (AlbumData album : albums) {
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

                // M3U playlist with direct MP3 URLs
                if (!album.page().tracks().isEmpty()) {
                    addEntry(zos, folder + "/playlist.m3u", buildM3u(album.item(), album.page().tracks()));
                }

                if (!missing.isEmpty()) {
                    addEntry(zos, folder + "/missing.txt",
                        String.join("\n", missing).getBytes(StandardCharsets.UTF_8));
                }
            }
        }

        return baos.toByteArray();
    }

    private byte[] buildM3u(InvoiceItem item, List<TrackInfo> tracks) {
        StringBuilder sb = new StringBuilder();
        sb.append("#EXTM3U\n");
        sb.append("#PLAYLIST:").append(item.artista()).append(" - ").append(item.album()).append("\n");
        for (TrackInfo track : tracks) {
            String displayName = track.label();
            if (!track.name().isBlank()) displayName += " - " + track.name();
            sb.append("#EXTINF:-1,").append(displayName).append("\n");
            sb.append(track.mp3Url()).append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
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
