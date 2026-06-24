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
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.web.util.UriUtils;

@Slf4j
@Service
public class VinylFutureAssetService {

    private static final String USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 30_000;
    private static final int MAX_IMAGE_SIZE = 10 * 1024 * 1024;
    private static final int MAX_AUDIO_SIZE = 30 * 1024 * 1024;
    private static final int MAX_DOWNLOAD_ATTEMPTS = 3;
    private static final String PUBLIC_PREFIX = "/api/importar/vinylfuture/media/";

    private final Path mediaDirectory;

    public VinylFutureAssetService(
            @Value("${sonograma.vinylfuture.media-directory:./data/vinylfuture-media}") String directory) {
        this.mediaDirectory = Path.of(directory).toAbsolutePath().normalize();
    }

    public VinylPageData storeAssets(InvoiceItem item, VinylPageData page) {
        AssetStoreResult result = storeAssetsWithResult(item, page);
        return result == null ? null : result.page();
    }

    public AssetStoreResult storeAssetsWithResult(InvoiceItem item, VinylPageData page) {
        if (page == null) return null;

        String folder = discFolder(item, page);
        StoredAsset coverAsset = store(
            page.frontImageUrl(),
            folder + "/cover." + extensionFromUrl(page.frontImageUrl(), "jpg"),
            MAX_IMAGE_SIZE,
            "image/*"
        );
        String coverUrl = coverAsset.publicUrlOrFallback(page.frontImageUrl());
        int coversDownloaded = coverAsset.downloaded() ? 1 : 0;
        int failedMediaDownloads = coverAsset.failed() ? 1 : 0;
        int mp3Downloaded = 0;

        List<TrackInfo> tracks = new ArrayList<>();
        List<TrackInfo> sourceTracks = page.tracks() == null ? List.of() : page.tracks();
        for (int i = 0; i < sourceTracks.size(); i++) {
            TrackInfo track = sourceTracks.get(i);
            String audioUrl = track.mp3Url();
            if (audioUrl != null && !audioUrl.isBlank()) {
                String position = firstNonBlank(track.label(), String.format("%02d", i + 1));
                String trackName = firstNonBlank(track.name(), "Track " + position);
                String filename = folder + "/" + sanitize(position, 12)
                    + " - " + sanitize(trackName, 80) + ".mp3";
                StoredAsset audioAsset = store(track.mp3Url(), filename, MAX_AUDIO_SIZE, "audio/mpeg,*/*");
                audioUrl = audioAsset.publicUrlOrFallback(track.mp3Url());
                if (audioAsset.downloaded()) {
                    mp3Downloaded++;
                }
                if (audioAsset.failed()) {
                    failedMediaDownloads++;
                }
            }
            tracks.add(new TrackInfo(track.label(), track.name(), audioUrl, track.youtubeUrl()));
        }

        VinylPageData storedPage = new VinylPageData(
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
        return new AssetStoreResult(storedPage, coversDownloaded, mp3Downloaded, failedMediaDownloads);
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
            return safeFile(decodePath(urlOrPath.substring(index + PUBLIC_PREFIX.length())));
        } catch (IOException ex) {
            return null;
        }
    }

    public String relativePath(String urlOrPath) {
        Path localPath = localPath(urlOrPath);
        if (localPath == null) return null;
        return mediaDirectory.relativize(localPath).toString().replace('\\', '/');
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

            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(mediaDirectory, "vinylfuture-", ".download");
            try {
                downloadToWithRetries(sourceUrl, temporary, maxSize, accept);
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

    private void downloadToWithRetries(String sourceUrl, Path target, int maxSize, String accept)
            throws IOException {
        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_DOWNLOAD_ATTEMPTS; attempt++) {
            try {
                downloadTo(sourceUrl, target, maxSize, accept);
                if (attempt > 1) {
                    log.info("Asset VinylFuture recuperado en reintento {}/{}: {}",
                        attempt, MAX_DOWNLOAD_ATTEMPTS, sourceUrl);
                }
                return;
            } catch (IOException ex) {
                lastError = ex;
                if (attempt >= MAX_DOWNLOAD_ATTEMPTS) {
                    break;
                }
                log.warn("Reintento asset VinylFuture {}/{} para '{}': {}",
                    attempt + 1, MAX_DOWNLOAD_ATTEMPTS, sourceUrl, ex.getMessage());
                sleepBeforeRetry(attempt);
            }
        }
        throw lastError;
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
                 OutputStream output = new BufferedOutputStream(Files.newOutputStream(
                     target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
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
        String relative = mediaDirectory.relativize(file).toString().replace('\\', '/');
        return PUBLIC_PREFIX + UriUtils.encodePath(relative, StandardCharsets.UTF_8);
    }

    private String discFolder(InvoiceItem item, VinylPageData page) {
        String code = firstNonBlank(page.code(), item.codigoCatalogo(), "sin-codigo");
        String artist = firstNonBlank(page.artist(), item.artista(), "sin-artista");
        String album = firstNonBlank(page.title(), item.album(), "sin-album");
        return sanitize(code, 50) + " - " + sanitize(artist, 70) + " - " + sanitize(album, 90);
    }

    private String extensionFromUrl(String url, String fallback) {
        if (url == null) return fallback;
        String path = url.split("\\?", 2)[0].toLowerCase(Locale.ROOT);
        if (path.endsWith(".jpeg")) return "jpg";
        for (String extension : List.of("jpeg", "jpg", "png", "webp", "gif")) {
            if (path.endsWith("." + extension)) return extension;
        }
        return fallback;
    }

    private String sanitize(String value, int maxLength) {
        String fallback = value == null || value.isBlank() ? "sin-datos" : value;
        String sanitized = fallback.replaceAll("[/\\\\:*?\"<>|]", "_")
            .replaceAll("\\p{Cntrl}", "")
            .replaceAll("\\s+", " ")
            .strip();
        if (sanitized.isBlank()) sanitized = "sin-datos";
        return sanitized.length() > maxLength ? sanitized.substring(0, maxLength).strip() : sanitized;
    }

    private String decodePath(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private void sleepBeforeRetry(int attempt) throws IOException {
        try {
            Thread.sleep(500L * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("descarga interrumpida", ex);
        }
    }

    public record AssetStoreResult(
        VinylPageData page,
        int coversDownloaded,
        int mp3Downloaded,
        int failedMediaDownloads
    ) {}

    private record StoredAsset(String publicUrl, boolean downloaded, boolean failed) {
        static StoredAsset success(String publicUrl, boolean downloaded) {
            return new StoredAsset(publicUrl, downloaded, false);
        }

        static StoredAsset missing() {
            return new StoredAsset(null, false, false);
        }

        static StoredAsset failure() {
            return new StoredAsset(null, false, true);
        }

        String publicUrlOrFallback(String fallback) {
            return publicUrl != null && !publicUrl.isBlank() ? publicUrl : fallback;
        }
    }
}
