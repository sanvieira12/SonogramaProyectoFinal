package com.sonograma.service;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.TrackInfo;
import com.sonograma.dto.VinylPageData;
import com.sonograma.service.VinylFutureAssetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a ZIP containing the import CSV, cover images, and MP3 previews.
 * Remote assets and the final ZIP are kept on disk to avoid exhausting the JVM heap.
 */
@Slf4j
@Service
public class ZipBundleService {

    private static final String USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final int IMAGE_TIMEOUT_MS = 15_000;
    private static final int MAX_IMAGE_SIZE = 5 * 1024 * 1024;
    private static final int MP3_TIMEOUT_MS = 30_000;
    private static final int MAX_MP3_SIZE = 20 * 1024 * 1024;
    private static final int DOWNLOAD_THREADS = 4;
    private static final int MAX_DOWNLOAD_ATTEMPTS = 3;
    private static final DateTimeFormatter EXPORT_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ExecutorService downloadPool = Executors.newFixedThreadPool(DOWNLOAD_THREADS);
    private final VinylFutureAssetService vinylFutureAssetService;

    public ZipBundleService(VinylFutureAssetService vinylFutureAssetService) {
        this.vinylFutureAssetService = vinylFutureAssetService;
    }

    public Path buildZip(String csv, Map<InvoiceItem, Optional<VinylPageData>> pageDataMap) throws IOException {
        record TrackResult(String filename, Path path) {}
        record Mp3Task(int albumIndex, String filename) {}
        String root = "vinylfuture-export-" + LocalDateTime.now().format(EXPORT_TS);

        List<Map.Entry<InvoiceItem, VinylPageData>> entries = pageDataMap.entrySet().stream()
            .filter(entry -> entry.getValue().isPresent())
            .map(entry -> Map.entry(entry.getKey(), entry.getValue().get()))
            .toList();
        int totalTracks = entries.stream()
            .map(Map.Entry::getValue)
            .map(VinylPageData::tracks)
            .filter(Objects::nonNull)
            .mapToInt(List::size)
            .sum();
        log.info("ZIP VinylFuture: media resolution start. products={}, trackCandidates={}", entries.size(), totalTracks);

        AtomicInteger coversReady = new AtomicInteger();
        AtomicInteger mp3Ready = new AtomicInteger();
        AtomicInteger failedMediaDownloads = new AtomicInteger();

        List<CompletableFuture<Path>> imageFutures = entries.stream()
            .map(entry -> CompletableFuture.supplyAsync(
                () -> resolveAsset(
                    entry.getValue().frontImageUrl(),
                    IMAGE_TIMEOUT_MS,
                    MAX_IMAGE_SIZE,
                    "cover-",
                    MediaType.COVER,
                    coversReady,
                    mp3Ready,
                    failedMediaDownloads
                ),
                downloadPool
            ))
            .toList();

        List<Mp3Task> mp3Tasks = new ArrayList<>();
        List<CompletableFuture<Path>> mp3Futures = new ArrayList<>();
        for (int albumIndex = 0; albumIndex < entries.size(); albumIndex++) {
            List<TrackInfo> tracks = entries.get(albumIndex).getValue().tracks();
            for (int trackIndex = 0; trackIndex < tracks.size(); trackIndex++) {
                TrackInfo track = tracks.get(trackIndex);
                if (track.mp3Url() == null || track.mp3Url().isBlank()) {
                    continue;
                }

                String position = firstNonBlank(track.label(), String.format("%02d", trackIndex + 1));
                String trackName = firstNonBlank(track.name(), "Track " + position);
                String filename = sanitizePath(position) + " - " + sanitizePath(trackName) + ".mp3";
                mp3Tasks.add(new Mp3Task(albumIndex, filename));
                mp3Futures.add(CompletableFuture.supplyAsync(
                    () -> resolveAsset(
                        track.mp3Url(),
                        MP3_TIMEOUT_MS,
                        MAX_MP3_SIZE,
                        "track-",
                        MediaType.MP3,
                        coversReady,
                        mp3Ready,
                        failedMediaDownloads
                    ),
                    downloadPool
                ));
            }
        }

        List<Path> imagePaths = imageFutures.stream().map(CompletableFuture::join).toList();
        List<Path> mp3Paths = mp3Futures.stream().map(CompletableFuture::join).toList();
        log.info(
            "ZIP VinylFuture: media resolution finished. coversDownloaded={}, mp3FilesDownloaded={}, failedMediaDownloads={}",
            coversReady.get(),
            mp3Ready.get(),
            failedMediaDownloads.get()
        );

        Map<Integer, List<TrackResult>> tracksByAlbum = new HashMap<>();
        for (int i = 0; i < mp3Tasks.size(); i++) {
            Mp3Task task = mp3Tasks.get(i);
            tracksByAlbum.computeIfAbsent(task.albumIndex(), ignored -> new ArrayList<>())
                .add(new TrackResult(task.filename(), mp3Paths.get(i)));
        }

        List<Path> downloadedFiles = new ArrayList<>();
        imagePaths.stream().filter(Objects::nonNull).forEach(downloadedFiles::add);
        mp3Paths.stream().filter(Objects::nonNull).forEach(downloadedFiles::add);

        Path zipPath = Files.createTempFile("vinylfuture-import-", ".zip");
        try {
            log.info("ZIP VinylFuture generation start: root='{}', target='{}'", root, zipPath);
            try (OutputStream fileOut = new BufferedOutputStream(Files.newOutputStream(zipPath));
                 ZipOutputStream zip = new ZipOutputStream(fileOut, StandardCharsets.UTF_8)) {
                byte[] csvBytes = csv.getBytes(StandardCharsets.UTF_8);
                addEntry(zip, root + "/import.csv", csvBytes);
                addEntry(zip, root + "/data/import.csv", csvBytes);
                addEntry(zip, root + "/data/catalog.csv", csvBytes);

                for (int albumIndex = 0; albumIndex < entries.size(); albumIndex++) {
                    InvoiceItem item = entries.get(albumIndex).getKey();
                    VinylPageData page = entries.get(albumIndex).getValue();
                    String folder = root + "/media/" + mediaFolder(item, page);
                    List<String> missing = new ArrayList<>();

                    Path imagePath = imagePaths.get(albumIndex);
                    if (imagePath != null) {
                        String relative = vinylFutureAssetService.relativePath(page.frontImageUrl());
                        String filename = relative != null && relative.contains("/")
                            ? relative.substring(relative.lastIndexOf('/') + 1)
                            : "cover." + guessExtension(page.frontImageUrl(), "jpg");
                        addFileEntry(zip, folder + "/" + filename, imagePath);
                    } else if (page.frontImageUrl() != null) {
                        missing.add("front image: " + page.frontImageUrl());
                    } else {
                        missing.add("front image: not found on product page");
                    }

                    for (TrackResult track : tracksByAlbum.getOrDefault(albumIndex, List.of())) {
                        if (track.path() != null) {
                            addFileEntry(zip, folder + "/" + track.filename(), track.path());
                        } else {
                            missing.add("audio: " + track.filename());
                        }
                    }

                    if (!missing.isEmpty()) {
                        addEntry(
                            zip,
                            folder + "/missing.txt",
                            String.join("\n", missing).getBytes(StandardCharsets.UTF_8)
                        );
                    }
                }
            }
            log.info("ZIP VinylFuture generation end: path='{}', bytes={}", zipPath, Files.size(zipPath));
            return zipPath;
        } catch (Exception e) {
            Files.deleteIfExists(zipPath);
            throw e;
        } finally {
            cleanup(downloadedFiles);
        }
    }

    private Path resolveAsset(
            String url,
            int timeout,
            int maxSize,
            String prefix,
            MediaType mediaType,
            AtomicInteger coversReady,
            AtomicInteger mp3Ready,
            AtomicInteger failedMediaDownloads) {
        if (url == null || url.isBlank()) return null;
        Path localPath = vinylFutureAssetService.localPath(url);
        if (localPath != null && Files.isRegularFile(localPath)) {
            try {
                Path tempFile = Files.createTempFile(prefix, ".download");
                Files.copy(localPath, tempFile, StandardCopyOption.REPLACE_EXISTING);
                incrementSuccessCounter(mediaType, coversReady, mp3Ready);
                return tempFile;
            } catch (IOException ex) {
                log.warn("No se pudo preparar asset local '{}': {}", localPath, ex.getMessage());
                failedMediaDownloads.incrementAndGet();
                return null;
            }
        }
        Path downloaded = downloadToTemp(url, timeout, maxSize, prefix);
        if (downloaded != null) {
            incrementSuccessCounter(mediaType, coversReady, mp3Ready);
        } else {
            failedMediaDownloads.incrementAndGet();
        }
        return downloaded;
    }

    private Path downloadToTemp(String url, int timeout, int maxSize, String prefix) {
        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_DOWNLOAD_ATTEMPTS; attempt++) {
            Path tempFile = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(timeout);
                connection.setReadTimeout(timeout);
                connection.setRequestProperty("User-Agent", USER_AGENT);

                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    throw new IOException("HTTP " + status);
                }

                long contentLength = connection.getContentLengthLong();
                if (contentLength > maxSize) {
                    throw new IOException("archivo supera el límite de " + maxSize + " bytes");
                }

                tempFile = Files.createTempFile(prefix, ".download");
                try (InputStream input = new BufferedInputStream(connection.getInputStream());
                     OutputStream output = new BufferedOutputStream(Files.newOutputStream(tempFile))) {
                    byte[] buffer = new byte[16 * 1024];
                    long total = 0;
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > maxSize) {
                            throw new IOException("archivo supera el límite de " + maxSize + " bytes");
                        }
                        output.write(buffer, 0, read);
                    }
                }
                if (attempt > 1) {
                    log.info("ZIP VinylFuture asset recovered on retry {}/{}: {}",
                        attempt, MAX_DOWNLOAD_ATTEMPTS, url);
                }
                return tempFile;
            } catch (Exception e) {
                lastError = e;
                if (tempFile != null) {
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException cleanupError) {
                        log.debug("No se pudo limpiar '{}': {}", tempFile, cleanupError.getMessage());
                    }
                }
                if (attempt < MAX_DOWNLOAD_ATTEMPTS) {
                    log.warn("ZIP VinylFuture retry {}/{} for '{}': {}",
                        attempt + 1, MAX_DOWNLOAD_ATTEMPTS, url, e.getMessage());
                    sleepBeforeRetry(attempt);
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        log.warn("Asset download failed '{}': {}", url, lastError != null ? lastError.getMessage() : "unknown error");
        return null;
    }

    private void cleanup(List<Path> files) {
        for (Path file : files) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                log.warn("No se pudo eliminar el archivo temporal '{}': {}", file, e.getMessage());
            }
        }
    }

    private void addFileEntry(ZipOutputStream zip, String name, Path file) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        Files.copy(file, zip);
        zip.closeEntry();
    }

    private void addEntry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private String guessExtension(String url, String fallback) {
        if (url == null) {
            return fallback;
        }
        int queryIndex = url.indexOf('?');
        String path = queryIndex > 0 ? url.substring(0, queryIndex) : url;
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < path.length() - 1) {
            String extension = path.substring(dotIndex + 1).toLowerCase();
            if (extension.matches("jpg|jpeg|png|gif|webp")) {
                return extension;
            }
        }
        return fallback;
    }

    private String sanitizePath(String name) {
        if (name == null || name.isBlank()) {
            return "sin-nombre";
        }
        String sanitized = name.replaceAll("[/\\\\:*?\"<>|]", "_")
            .replaceAll("\\p{Cntrl}", "")
            .replaceAll("\\s+", " ")
            .strip();
        return sanitized.isBlank() ? "sin-nombre" : sanitized;
    }

    private String mediaFolder(InvoiceItem item, VinylPageData page) {
        String code = firstNonBlank(page.code(), item.codigoCatalogo(), "sin-codigo");
        String artist = firstNonBlank(page.artist(), item.artista(), "sin-artista");
        String album = firstNonBlank(page.title(), item.album(), "sin-album");
        return truncate(sanitizePath(code), 50)
            + " - " + truncate(sanitizePath(artist), 70)
            + " - " + truncate(sanitizePath(album), 90);
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength).strip();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void incrementSuccessCounter(
            MediaType mediaType,
            AtomicInteger coversReady,
            AtomicInteger mp3Ready) {
        if (mediaType == MediaType.COVER) {
            coversReady.incrementAndGet();
        } else {
            mp3Ready.incrementAndGet();
        }
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(500L * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("descarga de ZIP interrumpida", ex);
        }
    }

    private enum MediaType {
        COVER,
        MP3
    }
}
