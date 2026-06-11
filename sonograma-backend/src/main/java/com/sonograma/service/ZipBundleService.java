package com.sonograma.service;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.TrackInfo;
import com.sonograma.dto.VinylPageData;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    private final ExecutorService downloadPool = Executors.newFixedThreadPool(DOWNLOAD_THREADS);

    public Path buildZip(String csv, Map<InvoiceItem, Optional<VinylPageData>> pageDataMap) throws IOException {
        record TrackResult(String filename, Path path) {}
        record Mp3Task(int albumIndex, String filename) {}

        List<Map.Entry<InvoiceItem, VinylPageData>> entries = pageDataMap.entrySet().stream()
            .filter(entry -> entry.getValue().isPresent())
            .map(entry -> Map.entry(entry.getKey(), entry.getValue().get()))
            .toList();

        List<CompletableFuture<Path>> imageFutures = entries.stream()
            .map(entry -> CompletableFuture.supplyAsync(
                () -> entry.getValue().frontImageUrl() == null
                    ? null
                    : downloadToTemp(
                        entry.getValue().frontImageUrl(),
                        IMAGE_TIMEOUT_MS,
                        MAX_IMAGE_SIZE,
                        "cover-"
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

                String trackName = track.name().isBlank() ? track.label() : track.name();
                String filename = String.format("%02d_%s.mp3", trackIndex + 1, sanitizePath(trackName));
                mp3Tasks.add(new Mp3Task(albumIndex, filename));
                mp3Futures.add(CompletableFuture.supplyAsync(
                    () -> downloadToTemp(track.mp3Url(), MP3_TIMEOUT_MS, MAX_MP3_SIZE, "track-"),
                    downloadPool
                ));
            }
        }

        List<Path> imagePaths = imageFutures.stream().map(CompletableFuture::join).toList();
        List<Path> mp3Paths = mp3Futures.stream().map(CompletableFuture::join).toList();

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
            try (OutputStream fileOut = new BufferedOutputStream(Files.newOutputStream(zipPath));
                 ZipOutputStream zip = new ZipOutputStream(fileOut, StandardCharsets.UTF_8)) {
                addEntry(zip, "import.csv", csv.getBytes(StandardCharsets.UTF_8));

                for (int albumIndex = 0; albumIndex < entries.size(); albumIndex++) {
                    InvoiceItem item = entries.get(albumIndex).getKey();
                    VinylPageData page = entries.get(albumIndex).getValue();
                    String folder = sanitizePath(
                        item.artista() + " - " + item.album() + " - " + item.codigoCatalogo()
                    );
                    String code = sanitizeCode(item.codigoCatalogo());
                    List<String> missing = new ArrayList<>();

                    Path imagePath = imagePaths.get(albumIndex);
                    if (imagePath != null) {
                        String extension = guessExtension(page.frontImageUrl(), "jpg");
                        addFileEntry(zip, folder + "/images/" + code + "_front." + extension, imagePath);
                    } else if (page.frontImageUrl() != null) {
                        missing.add("front image: " + page.frontImageUrl());
                    } else {
                        missing.add("front image: not found on product page");
                    }

                    for (TrackResult track : tracksByAlbum.getOrDefault(albumIndex, List.of())) {
                        if (track.path() != null) {
                            addFileEntry(zip, folder + "/audio/" + track.filename(), track.path());
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
            return zipPath;
        } catch (Exception e) {
            Files.deleteIfExists(zipPath);
            throw e;
        } finally {
            cleanup(downloadedFiles);
        }
    }

    private Path downloadToTemp(String url, int timeout, int maxSize, String prefix) {
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
            return tempFile;
        } catch (Exception e) {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException cleanupError) {
                    log.debug("No se pudo limpiar '{}': {}", tempFile, cleanupError.getMessage());
                }
            }
            log.warn("Asset download failed '{}': {}", url, e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
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
        return name.replaceAll("[/\\\\:*?\"<>|]", "_").strip();
    }

    private String sanitizeCode(String code) {
        return code.replaceAll("[^A-Za-z0-9\\-]", "_");
    }
}
