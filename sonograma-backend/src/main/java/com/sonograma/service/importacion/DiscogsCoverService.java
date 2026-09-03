package com.sonograma.service.importacion;

import com.sonograma.dto.DiscogsCoverZipRow;
import com.sonograma.dto.DiscoImportPreviewDTO;
import com.sonograma.dto.TrackInfo;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class DiscogsCoverService {

    private static final String USER_AGENT =
            "SonogramaApp/1.0 +https://github.com/sanvieira12/SonogramaProyectoFinal";
    private static final int TIMEOUT_MS = 20_000;
    private static final int MAX_IMAGE_SIZE = 10 * 1024 * 1024;

    private final Path coversDirectory;

    public DiscogsCoverService(
            @Value("${discogs.covers.directory:./data/discogs-covers}") String directory
    ) {
        this.coversDirectory = Path.of(directory).toAbsolutePath().normalize();
    }

    public CoverResult download(String imageUrl, long releaseId) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return CoverResult.missing("Discogs no informó una portada");
        }
        try {
            Files.createDirectories(coversDirectory);
            String extension = extensionFromUrl(imageUrl);
            Path target = coversDirectory.resolve(releaseId + "." + extension);
            if (Files.isRegularFile(target) && Files.size(target) > 0) {
                return CoverResult.success(publicUrl(target), target, false);
            }

            Path temporary = Files.createTempFile(coversDirectory, releaseId + "-", ".download");
            try {
                downloadTo(imageUrl, temporary);
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
            log.info("Portada Discogs descargada release={} archivo={}", releaseId, target);
            return CoverResult.success(publicUrl(target), target, true);
        } catch (Exception ex) {
            log.warn("No se pudo descargar portada Discogs release={} url={}: {}",
                    releaseId, imageUrl, ex.getMessage());
            return CoverResult.failure(imageUrl, ex.getMessage());
        }
    }

    /** Reuses an already downloaded individual cover without opening the network. */
    public CoverResult existing(String publicUrl) {
        Path path = localPath(publicUrl);
        try {
            if (path != null && Files.isRegularFile(path) && Files.size(path) > 0) {
                return CoverResult.success(publicUrl(path), path, false);
            }
        } catch (IOException ignored) {
            // The caller may still try the original remote URL.
        }
        return CoverResult.missing("La portada aún no está disponible localmente.");
    }

    /** Writes metadata, permitted links and an available cover; never stock or audio media. */
    public void writeManualReleaseZip(OutputStream output, DiscoImportPreviewDTO preview) throws IOException {
        Set<String> usedNames = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(output))) {
            CoverResult cover = existing(preview.getImagenUrl());
            if (!cover.available()) cover = download(preview.getImagenUrl(), preview.getDiscogsReleaseId());
            if (cover.available() && cover.localPath() != null && Files.isRegularFile(cover.localPath())) {
                zip.putNextEntry(new ZipEntry(uniqueZipEntryName("cover" + extensionWithDot(cover.localPath()), usedNames)));
                Files.copy(cover.localPath(), zip);
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry(uniqueZipEntryName("release.json", usedNames)));
            zip.write(manualReleaseJson(preview).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(uniqueZipEntryName("tracks-and-links.txt", usedNames)));
            zip.write(manualTracks(preview).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    public Resource load(String filename) throws IOException {
        Path file = safeFile(filename);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Portada no encontrada");
        }
        return new UrlResource(file.toUri());
    }

    public String contentType(String filename) {
        try {
            String detected = Files.probeContentType(safeFile(filename));
            return detected == null ? "image/jpeg" : detected;
        } catch (IOException ex) {
            return "image/jpeg";
        }
    }

    public int clearStoredCovers() {
        if (!Files.exists(coversDirectory)) {
            return 0;
        }
        try (var paths = Files.walk(coversDirectory)) {
            List<Path> toDelete = paths
                    .sorted(Comparator.reverseOrder())
                    .toList();
            int deletedFiles = 0;
            for (Path path : toDelete) {
                if (path.equals(coversDirectory)) {
                    continue;
                }
                if (Files.isRegularFile(path)) {
                    deletedFiles++;
                }
                Files.deleteIfExists(path);
            }
            return deletedFiles;
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudieron limpiar las portadas Discogs", ex);
        }
    }

    public Path preparedZipPath(long jobId) throws IOException {
        Path directory = coversDirectory.resolve("zip");
        Files.createDirectories(directory);
        return directory.resolve("discogs-covers-" + jobId + ".zip");
    }

    public ZipBuildResult buildZip(Path zipPath, List<DiscogsCoverZipRow> rows,
                                   Consumer<ZipProgress> progress) throws IOException {
        Files.createDirectories(zipPath.toAbsolutePath().normalize().getParent());
        Path temporary = Files.createTempFile(zipPath.getParent(), "discogs-covers-", ".part");
        Set<String> usedNames = new HashSet<>();
        List<ZipError> errors = new ArrayList<>();
        List<Integer> missingLocalRows = new ArrayList<>();
        List<DiscogsCoverZipRow> candidates = rows.stream()
                .filter(row -> row.getResolvedReleaseId() != null)
                .toList();
        int added = 0;
        int failed = 0;
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(temporary));
             ZipOutputStream zip = new ZipOutputStream(output)) {
            addSummary(zip, rows, usedNames);
            for (int index = 0; index < candidates.size(); index++) {
                DiscogsCoverZipRow row = candidates.get(index);
                Long releaseId = row.getResolvedReleaseId();
                Path cover = localCover(row);
                if (cover == null || !Files.isRegularFile(cover)) {
                    failed++;
                    missingLocalRows.add(row.getSourceExcelRowNumber());
                    errors.add(new ZipError(row, "MISSING_LOCAL_FILE",
                            "La portada descargada no está disponible en el almacenamiento local."));
                    progress.accept(new ZipProgress(candidates.size(), index + 1, added, failed,
                            currentRelease(row)));
                    continue;
                }
                String filename = row.getSourceExcelRowNumber() + "_" + releaseId
                        + "_" + sanitize(row.getArtist())
                        + "_" + sanitize(row.getTitle())
                        + extensionWithDot(cover);
                zip.putNextEntry(new ZipEntry(uniqueZipEntryName(filename, usedNames)));
                Files.copy(cover, zip);
                zip.closeEntry();
                added++;
                progress.accept(new ZipProgress(candidates.size(), index + 1, added, failed,
                        currentRelease(row)));
            }
            collectRowErrors(rows, errors);
            if (!errors.isEmpty()) addErrors(zip, errors, usedNames);
            progress.accept(new ZipProgress(candidates.size(), candidates.size(), added, failed, null));
        } catch (Exception ex) {
            Files.deleteIfExists(temporary);
            throw ex;
        }
        try {
            Files.move(temporary, zipPath, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(temporary, zipPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return new ZipBuildResult(zipPath, candidates.size(), added, failed, errors.size(),
                List.copyOf(missingLocalRows));
    }

    private Path localCover(DiscogsCoverZipRow row) {
        String coverLocalPath = row.getCoverLocalPath();
        if (coverLocalPath != null && !coverLocalPath.isBlank()) {
            try {
                Path candidate = Path.of(coverLocalPath).toAbsolutePath().normalize();
                if (candidate.startsWith(coversDirectory)) return candidate;
            } catch (RuntimeException invalidPath) {
                log.warn("Ruta local de portada Discogs inválida fila={}: {}",
                        row.getSourceExcelRowNumber(), coverLocalPath);
            }
        }
        return localPath(row.getImageUrl());
    }

    private String currentRelease(DiscogsCoverZipRow row) {
        String artist = row.getArtist() == null ? "Sin artista" : row.getArtist();
        String title = row.getTitle() == null ? "Sin título" : row.getTitle();
        String value = artist + " – " + title;
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private void collectRowErrors(List<DiscogsCoverZipRow> rows, List<ZipError> errors) {
        for (DiscogsCoverZipRow row : rows) {
            if (row.getSourceDiscogsId() == null) {
                errors.add(new ZipError(row, "MISSING_DISCOGS_LINK", concise(
                        row.getWarningMessage(), "No se detectó un link de Discogs.")));
            } else if (row.getMetadataErrorCode() != null) {
                errors.add(new ZipError(row, row.getMetadataErrorCode(), concise(
                        row.getErrorMessage(), "No se pudo obtener metadata de Discogs.")));
            }
            if (row.getPriceRaw() != null && row.getPriceUyu() == null) {
                errors.add(new ZipError(row, "NON_NUMERIC_PRICE",
                        "El precio '" + row.getPriceRaw() + "' requiere revisión manual."));
            }
            if (row.getCoverErrorCode() != null && errors.stream().noneMatch(error ->
                    error.row() == row && error.errorCode().equals(row.getCoverErrorCode()))) {
                errors.add(new ZipError(row, row.getCoverErrorCode(), concise(
                        row.getWarningMessage(), "La portada no está disponible.")));
            }
        }
    }

    private String concise(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String singleLine = value.replaceAll("\\s+", " ").trim();
        return singleLine.length() > 500 ? singleLine.substring(0, 500) : singleLine;
    }

    private void addErrors(ZipOutputStream zip, List<ZipError> errors, Set<String> usedNames) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append("excel_row,discogs_url,source_type,source_discogs_id,resolved_release_id,artist,title,error_code,error_message\n");
        for (ZipError error : errors) {
            DiscogsCoverZipRow row = error.row();
            csv.append(csv(row.getSourceExcelRowNumber())).append(',')
                    .append(csv(row.getDiscogsUrl())).append(',')
                    .append(csv(row.getSourceType())).append(',')
                    .append(csv(row.getSourceDiscogsId())).append(',')
                    .append(csv(row.getResolvedReleaseId())).append(',')
                    .append(csv(row.getArtist())).append(',')
                    .append(csv(row.getTitle())).append(',')
                    .append(csv(error.errorCode())).append(',')
                    .append(csv(error.errorMessage())).append('\n');
        }
        zip.putNextEntry(new ZipEntry(uniqueZipEntryName("errors.csv", usedNames)));
        zip.write(csv.toString().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private void addSummary(ZipOutputStream zip, List<DiscogsCoverZipRow> rows,
                            Set<String> usedNames) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append("excel_row,discogs_url,source_type,source_discogs_id,resolved_release_id,artist,title,price_uyu,price_raw,condition,source_status,metadata_status,cover_status,youtube_status,catalog_import_status,imported_catalog_id,qr_id\n");
        for (DiscogsCoverZipRow row : rows) {
            csv.append(csv(row.getSourceExcelRowNumber())).append(',')
                    .append(csv(row.getDiscogsUrl())).append(',')
                    .append(csv(row.getSourceType())).append(',')
                    .append(csv(row.getSourceDiscogsId())).append(',')
                    .append(csv(row.getResolvedReleaseId())).append(',')
                    .append(csv(row.getArtist())).append(',')
                    .append(csv(row.getTitle())).append(',')
                    .append(csv(row.getPriceUyu())).append(',')
                    .append(csv(row.getPriceRaw())).append(',')
                    .append(csv(row.getCondition())).append(',')
                    .append(csv(row.getSourceStatus())).append(',')
                    .append(csv(row.getMetadataStatus())).append(',')
                    .append(csv(row.getCoverStatus())).append(',')
                    .append(csv(row.getYoutubeStatus())).append(',')
                    .append(csv(row.getCatalogImportStatus())).append(',')
                    .append(csv(row.getCatalogDiscoId())).append(',')
                    .append(csv(row.getCodigoQr())).append('\n');
        }
        zip.putNextEntry(new ZipEntry(uniqueZipEntryName("discogs-summary.csv", usedNames)));
        zip.write(csv.toString().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String csv(Object value) {
        if (value == null) return "";
        String text = value instanceof BigDecimal decimal
                ? decimal.stripTrailingZeros().toPlainString()
                : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private String uniqueZipEntryName(String desiredName, Set<String> usedNames) {
        String sanitized = sanitizeZipPath(desiredName);
        if (usedNames.add(sanitized)) return sanitized;
        int dot = sanitized.lastIndexOf('.');
        String base = dot >= 0 ? sanitized.substring(0, dot) : sanitized;
        String extension = dot >= 0 ? sanitized.substring(dot) : "";
        int suffix = 2;
        String candidate;
        do {
            candidate = base + "-" + suffix++ + extension;
        } while (!usedNames.add(candidate));
        return candidate;
    }

    private String sanitizeZipPath(String value) {
        String sanitized = value.replaceAll("[/\\\\:*?\"<>|]", "_").strip();
        return sanitized.isBlank() ? "archivo" : sanitized;
    }

    private void downloadTo(String imageUrl, Path target) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(imageUrl).toURL().openConnection();
        try {
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "image/*");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status);
            }
            if (connection.getContentLengthLong() > MAX_IMAGE_SIZE) {
                throw new IOException("La portada supera el límite permitido");
            }
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 OutputStream output = new BufferedOutputStream(Files.newOutputStream(target))) {
                byte[] buffer = new byte[16 * 1024];
                long total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_IMAGE_SIZE) {
                        throw new IOException("La portada supera el límite permitido");
                    }
                    output.write(buffer, 0, read);
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private Path localPath(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        String prefix = "/api/importaciones/discogs/covers/";
        int index = imageUrl.indexOf(prefix);
        if (index < 0) return null;
        try {
            return safeFile(imageUrl.substring(index + prefix.length()));
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private Path safeFile(String filename) throws IOException {
        Path file = coversDirectory.resolve(filename).normalize();
        if (!file.startsWith(coversDirectory)) {
            throw new IOException("Nombre de portada inválido");
        }
        return file;
    }

    private String publicUrl(Path file) {
        return "/api/importaciones/discogs/covers/" + file.getFileName();
    }

    private String extensionFromUrl(String url) {
        String path = url.split("\\?", 2)[0].toLowerCase(Locale.ROOT);
        for (String extension : List.of("jpeg", "jpg", "png", "webp")) {
            if (path.endsWith("." + extension)) return extension;
        }
        return "jpg";
    }

    private String extensionWithDot(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : ".jpg";
    }

    private String sanitize(String value) {
        String fallback = value == null || value.isBlank() ? "Sin datos" : value;
        String sanitized = fallback.replaceAll("[/\\\\:*?\"<>|]", "_").strip();
        return sanitized.length() > 100 ? sanitized.substring(0, 100).strip() : sanitized;
    }

    private String manualReleaseJson(DiscoImportPreviewDTO preview) {
        return "{\n"
                + "  \"discogsReleaseId\": " + preview.getDiscogsReleaseId() + ",\n"
                + "  \"discogsUrl\": \"" + json(preview.getDiscogsUrl()) + "\",\n"
                + "  \"artist\": \"" + json(preview.getArtista()) + "\",\n"
                + "  \"title\": \"" + json(preview.getAlbum()) + "\",\n"
                + "  \"year\": " + (preview.getAnio() == null ? "null" : preview.getAnio()) + ",\n"
                + "  \"label\": \"" + json(preview.getSello()) + "\",\n"
                + "  \"format\": \"" + json(preview.getFormato()) + "\",\n"
                + "  \"genre\": \"" + json(preview.getGenero()) + "\",\n"
                + "  \"style\": \"" + json(preview.getEstilo()) + "\"\n"
                + "}\n";
    }

    private String manualTracks(DiscoImportPreviewDTO preview) {
        StringBuilder text = new StringBuilder("Tracklist\n")
                .append(preview.getTracklist() == null ? "" : preview.getTracklist()).append("\n\nLinks\n");
        if (preview.getTracks() != null) {
            for (TrackInfo track : preview.getTracks()) {
                text.append(nullToEmpty(track.label())).append(" ").append(nullToEmpty(track.name()));
                if (track.youtubeUrl() != null) text.append(" | YouTube: ").append(track.youtubeUrl());
                if (track.mp3Url() != null) text.append(" | Preview: ").append(track.mp3Url());
                text.append('\n');
            }
        }
        return text.toString();
    }

    private String json(String value) {
        return nullToEmpty(value).replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }

    private String nullToEmpty(String value) { return value == null ? "" : value; }

    public record CoverResult(
            boolean available,
            boolean downloaded,
            String publicUrl,
            Path localPath,
            String warning
    ) {
        static CoverResult success(String publicUrl, Path localPath, boolean downloaded) {
            return new CoverResult(true, downloaded, publicUrl, localPath, null);
        }

        static CoverResult missing(String warning) {
            return new CoverResult(false, false, null, null, warning);
        }

        static CoverResult failure(String fallbackUrl, String warning) {
            return new CoverResult(false, false, fallbackUrl, null, warning);
        }
    }

    public record ZipProgress(
            int total,
            int processed,
            int added,
            int failed,
            String currentRelease
    ) {}

    public record ZipBuildResult(
            Path path,
            int total,
            int added,
            int failed,
            int warningCount,
            List<Integer> missingLocalRows
    ) {}

    private record ZipError(DiscogsCoverZipRow row, String errorCode, String errorMessage) {}
}
