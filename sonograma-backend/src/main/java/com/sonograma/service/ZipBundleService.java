package com.sonograma.service;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.TrackInfo;
import com.sonograma.dto.VinylPageData;
import com.sonograma.service.VinylFutureAssetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    private static final int DOWNLOAD_THREADS = 4;
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
                Set<String> usedEntryNames = new HashSet<>();
                byte[] csvBytes = csv.getBytes(StandardCharsets.UTF_8);
                addEntry(zip, root + "/import.csv", csvBytes, usedEntryNames);
                addEntry(zip, root + "/data/import.csv", csvBytes, usedEntryNames);
                addEntry(zip, root + "/data/catalog.csv", csvBytes, usedEntryNames);

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
                        addFileEntry(zip, folder + "/" + filename, imagePath, usedEntryNames);
                    } else if (page.frontImageUrl() != null) {
                        missing.add("front image: " + page.frontImageUrl());
                    } else {
                        missing.add("front image: not found on product page");
                    }

                    for (TrackResult track : tracksByAlbum.getOrDefault(albumIndex, List.of())) {
                        if (track.path() != null) {
                            addFileEntry(zip, folder + "/" + track.filename(), track.path(), usedEntryNames);
                        } else {
                            missing.add("audio: " + track.filename());
                        }
                    }

                    if (!missing.isEmpty()) {
                        addEntry(
                            zip,
                            folder + "/missing.txt",
                            String.join("\n", missing).getBytes(StandardCharsets.UTF_8),
                            usedEntryNames
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
        log.warn("ZIP VinylFuture local asset unavailable. url='{}', resolvedPath='{}'", url, localPath);
        failedMediaDownloads.incrementAndGet();
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

    private void addFileEntry(ZipOutputStream zip, String desiredName, Path file, Set<String> usedNames) throws IOException {
        String name = uniqueZipEntryName(desiredName, usedNames);
        if (!name.equals(desiredName)) {
            log.warn("ZIP VinylFuture duplicate media path renamed: '{}' -> '{}'", desiredName, name);
        }
        zip.putNextEntry(new ZipEntry(name));
        Files.copy(file, zip);
        zip.closeEntry();
    }

    private void addEntry(ZipOutputStream zip, String desiredName, byte[] data, Set<String> usedNames) throws IOException {
        String name = uniqueZipEntryName(desiredName, usedNames);
        if (!name.equals(desiredName)) {
            log.warn("ZIP VinylFuture duplicate entry renamed: '{}' -> '{}'", desiredName, name);
        }
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    String uniqueZipEntryName(String desiredName, Set<String> usedNames) {
        String normalized = sanitizeZipEntryName(desiredName);
        if (usedNames.add(normalized)) {
            return normalized;
        }
        int slash = normalized.lastIndexOf('/');
        String folder = slash >= 0 ? normalized.substring(0, slash + 1) : "";
        String filename = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        String extension = dot > 0 ? filename.substring(dot) : "";
        for (int counter = 2; ; counter++) {
            String candidate = folder + base + "-" + counter + extension;
            if (usedNames.add(candidate)) {
                return candidate;
            }
        }
    }

    private String sanitizeZipEntryName(String desiredName) {
        String[] parts = desiredName.replace('\\', '/').split("/");
        List<String> sanitizedParts = new ArrayList<>();
        for (String part : parts) {
            if (part == null || part.isBlank() || ".".equals(part) || "..".equals(part)) {
                sanitizedParts.add("sin-nombre");
                continue;
            }
            sanitizedParts.add(sanitizePath(part));
        }
        return String.join("/", sanitizedParts);
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

    private enum MediaType {
        COVER,
        MP3
    }
}
