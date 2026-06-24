package com.sonograma.service;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.TrackInfo;
import com.sonograma.dto.VinylPageData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class VinylFutureAssetService {

    private static final String USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 30_000;
    private static final int MAX_IMAGE_SIZE = 10 * 1024 * 1024;
    private static final int MAX_AUDIO_SIZE = 30 * 1024 * 1024;
    private static final String PUBLIC_PREFIX = "/api/importar/vinylfuture/media/";

    private final Path mediaDirectory;

    public VinylFutureAssetService(
            @Value("${sonograma.vinylfuture.media-directory:./data/vinylfuture-media}") String directory) {
        this.mediaDirectory = Path.of(directory).toAbsolutePath().normalize();
    }

    public VinylPageData storeAssets(InvoiceItem item, VinylPageData page) {
        if (page == null) return null;

        String key = assetKey(item, page);
        String coverUrl = store(
            page.frontImageUrl(),
            key + "-cover." + extensionFromUrl(page.frontImageUrl(), "jpg"),
            MAX_IMAGE_SIZE,
            "image/*"
        ).publicUrlOrFallback(page.frontImageUrl());

        List<TrackInfo> tracks = new ArrayList<>();
        List<TrackInfo> sourceTracks = page.tracks() == null ? List.of() : page.tracks();
        for (int i = 0; i < sourceTracks.size(); i++) {
            TrackInfo track = sourceTracks.get(i);
            String audioUrl = track.mp3Url();
            if (audioUrl != null && !audioUrl.isBlank()) {
                String trackName = firstNonBlank(track.name(), track.label(), "track-" + (i + 1));
                String filename = key + "-" + String.format("%02d", i + 1)
                    + "-" + sanitize(trackName, 70) + ".mp3";
                audioUrl = store(track.mp3Url(), filename, MAX_AUDIO_SIZE, "audio/mpeg,*/*")
                    .publicUrlOrFallback(track.mp3Url());
            }
            tracks.add(new TrackInfo(track.label(), track.name(), audioUrl, track.youtubeUrl()));
        }

        return new VinylPageData(
            page.sourceUrl(),
            page.artist(),
            page.title(),
            page.code(),
            page.label(),
            page.genre(),
            page.year(),
            page.country(),
            page.format(),
            page.condition(),
            page.description(),
            page.purchasePrice(),
            coverUrl,
            page.backImageUrl(),
            tracks
        );
    }

    public Resource load(String filename) throws IOException {
        Path file = safeFile(filename);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Asset VinylFuture no encontrado");
        }
        return new UrlResource(file.toUri());
    }

    public String contentType(String filename) {
        try {
            String detected = Files.probeContentType(safeFile(filename));
            if (detected != null) return detected;
        } catch (IOException ignored) {
        }
        return filename.toLowerCase(Locale.ROOT).endsWith(".mp3") ? "audio/mpeg" : "image/jpeg";
    }

    public Path localPath(String urlOrPath) {
        if (urlOrPath == null || urlOrPath.isBlank()) return null;
        int index = urlOrPath.indexOf(PUBLIC_PREFIX);
        if (index < 0) return null;
        try {
            return safeFile(urlOrPath.substring(index + PUBLIC_PREFIX.length()));
        } catch (IOException ex) {
            return null;
        }
    }

    private StoredAsset store(String sourceUrl, String filename, int maxSize, String accept) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return StoredAsset.missing();
        }
        if (localPath(sourceUrl) != null) {
            return StoredAsset.success(sourceUrl, false);
        }
        try {
            Files.createDirectories(mediaDirectory);
            Path target = safeFile(filename);
            if (Files.isRegularFile(target) && Files.size(target) > 0) {
                return StoredAsset.success(publicUrl(target), false);
            }

            Path temporary = Files.createTempFile(mediaDirectory, filename + "-", ".download");
            try {
                downloadTo(sourceUrl, temporary, maxSize, accept);
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
            log.info("Asset VinylFuture guardado: {} -> {}", sourceUrl, target);
            return StoredAsset.success(publicUrl(target), true);
        } catch (Exception ex) {
            log.warn("No se pudo guardar asset VinylFuture '{}': {}", sourceUrl, ex.getMessage());
            return StoredAsset.failure();
        }
    }

    private void downloadTo(String sourceUrl, Path target, int maxSize, String accept) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(sourceUrl).toURL().openConnection();
        try {
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", accept);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status);
            }
            long contentLength = connection.getContentLengthLong();
            if (contentLength > maxSize) {
                throw new IOException("asset supera el límite de " + maxSize + " bytes");
            }
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 OutputStream output = new BufferedOutputStream(Files.newOutputStream(target))) {
                byte[] buffer = new byte[16 * 1024];
                long total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > maxSize) {
                        throw new IOException("asset supera el límite de " + maxSize + " bytes");
                    }
                    output.write(buffer, 0, read);
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private Path safeFile(String filename) throws IOException {
        Path file = mediaDirectory.resolve(filename).normalize();
        if (!file.startsWith(mediaDirectory)) {
            throw new IOException("Nombre de asset inválido");
        }
        return file;
    }

    private String publicUrl(Path file) {
        return PUBLIC_PREFIX + file.getFileName();
    }

    private String assetKey(InvoiceItem item, VinylPageData page) {
        String raw = firstNonBlank(
            page.code(),
            item.codigoCatalogo(),
            page.artist() + "-" + page.title(),
            item.artista() + "-" + item.album(),
            "vinylfuture"
        );
        String hash = Integer.toHexString(firstNonBlank(page.sourceUrl(), raw).hashCode());
        return sanitize(raw, 80) + "-" + hash;
    }

    private String extensionFromUrl(String url, String fallback) {
        if (url == null) return fallback;
        String path = url.split("\\?", 2)[0].toLowerCase(Locale.ROOT);
        for (String extension : List.of("jpeg", "jpg", "png", "webp", "gif")) {
            if (path.endsWith("." + extension)) return extension;
        }
        return fallback;
    }

    private String sanitize(String value, int maxLength) {
        String fallback = value == null || value.isBlank() ? "sin-datos" : value;
        String sanitized = fallback.replaceAll("[/\\\\:*?\"<>|]", "_")
            .replaceAll("[^A-Za-z0-9._ -]", "_")
            .replaceAll("\\s+", "_")
            .strip();
        if (sanitized.isBlank()) sanitized = "sin-datos";
        return sanitized.length() > maxLength ? sanitized.substring(0, maxLength).strip() : sanitized;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private record StoredAsset(String publicUrl, boolean downloaded) {
        static StoredAsset success(String publicUrl, boolean downloaded) {
            return new StoredAsset(publicUrl, downloaded);
        }

        static StoredAsset missing() {
            return new StoredAsset(null, false);
        }

        static StoredAsset failure() {
            return new StoredAsset(null, false);
        }

        String publicUrlOrFallback(String fallback) {
            return publicUrl != null && !publicUrl.isBlank() ? publicUrl : fallback;
        }
    }
}
