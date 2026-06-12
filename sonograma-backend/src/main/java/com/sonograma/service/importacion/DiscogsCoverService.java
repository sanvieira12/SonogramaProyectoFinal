package com.sonograma.service.importacion;

import com.sonograma.entity.DiscogsImportRow;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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

    public Path buildZip(List<DiscogsImportRow> rows) throws IOException {
        Path zipPath = Files.createTempFile("discogs-covers-", ".zip");
        Set<Long> included = new HashSet<>();
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(zipPath));
             ZipOutputStream zip = new ZipOutputStream(output)) {
            for (DiscogsImportRow row : rows) {
                Long releaseId = row.getResolvedReleaseId() != null
                        ? row.getResolvedReleaseId()
                        : row.getDiscogsId();
                if (releaseId == null || !included.add(releaseId) || row.getImageUrl() == null) {
                    continue;
                }
                Path cover = localPath(row.getImageUrl());
                if (cover == null || !Files.isRegularFile(cover)) {
                    CoverResult downloaded = download(row.getImageUrl(), releaseId);
                    cover = downloaded.localPath();
                }
                if (cover == null || !Files.isRegularFile(cover)) {
                    continue;
                }
                String filename = sanitize(row.getArtist()) + " - "
                        + sanitize(row.getTitle()) + " - " + releaseId
                        + extensionWithDot(cover);
                zip.putNextEntry(new ZipEntry(filename));
                Files.copy(cover, zip);
                zip.closeEntry();
            }
        } catch (Exception ex) {
            Files.deleteIfExists(zipPath);
            throw ex;
        }
        return zipPath;
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
        String prefix = "/api/importaciones/discogs/covers/";
        int index = imageUrl.indexOf(prefix);
        if (index < 0) return null;
        try {
            return safeFile(imageUrl.substring(index + prefix.length()));
        } catch (IOException ex) {
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
}
