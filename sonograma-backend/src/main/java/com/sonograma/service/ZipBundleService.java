package com.sonograma.service;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.TrackInfo;
import com.sonograma.dto.VinylPageData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a ZIP using only already persisted local media to keep memory usage bounded.
 */
@Slf4j
@Service
public class ZipBundleService {

    private static final DateTimeFormatter EXPORT_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final VinylFutureAssetService vinylFutureAssetService;

    public ZipBundleService(VinylFutureAssetService vinylFutureAssetService) {
        this.vinylFutureAssetService = vinylFutureAssetService;
    }

    public Path buildZip(
            String csv,
            Map<InvoiceItem, Optional<VinylPageData>> pageDataMap,
            String zipRootName) throws IOException {
        String root = sanitizeRoot(firstNonBlank(
            zipRootName,
            "VinylFuture_Invoice_" + LocalDateTime.now().format(EXPORT_TS)
        ));
        Path zipPath = Files.createTempFile("vinylfuture-import-", ".zip");
        List<Path> temporaryFiles = new ArrayList<>();

        try (OutputStream fileOut = new BufferedOutputStream(Files.newOutputStream(zipPath));
             ZipOutputStream zip = new ZipOutputStream(fileOut, StandardCharsets.UTF_8)) {
            Set<String> usedEntryNames = new LinkedHashSet<>();
            addEntry(zip, root + "/import.csv", csv.getBytes(StandardCharsets.UTF_8), usedEntryNames);

            for (Map.Entry<InvoiceItem, Optional<VinylPageData>> entry : pageDataMap.entrySet()) {
                InvoiceItem item = entry.getKey();
                VinylPageData page = entry.getValue().orElse(null);
                String albumFolder = root + "/" + albumFolder(item, page);
                List<String> missing = new ArrayList<>();

                ConvertedAsset cover = resolveCover(item, page, temporaryFiles);
                if (cover.path() != null) {
                    addFileEntry(zip, albumFolder + "/Imagenes/" + coverFilename(item, page), cover.path(), usedEntryNames);
                }
                if (cover.failure() != null) {
                    missing.add(cover.failure());
                }

                List<TrackInfo> tracks = page == null || page.tracks() == null ? List.of() : page.tracks();
                if (tracks.isEmpty()) {
                    missing.add("Audio: no se encontraron previews para este album.");
                }
                for (int index = 0; index < tracks.size(); index++) {
                    ConvertedAsset audio = resolveAudio(item, tracks.get(index), index, temporaryFiles);
                    if (audio.path() != null) {
                        addFileEntry(zip, albumFolder + "/Audios/" + audio.filename(), audio.path(), usedEntryNames);
                    }
                    if (audio.failure() != null) {
                        missing.add(audio.failure());
                    }
                }

                if (page == null) {
                    missing.add(0, "Metadata: no se pudo asociar una pagina valida de Vinyl Future para este item.");
                }
                if (!missing.isEmpty()) {
                    addEntry(
                        zip,
                        albumFolder + "/missing_media.txt",
                        String.join("\n", missing).getBytes(StandardCharsets.UTF_8),
                        usedEntryNames
                    );
                }
            }
        } catch (Exception ex) {
            Files.deleteIfExists(zipPath);
            throw ex;
        } finally {
            cleanup(temporaryFiles);
        }

        log.info("ZIP VinylFuture generado: root='{}', path='{}', bytes={}", root, zipPath, Files.size(zipPath));
        return zipPath;
    }

    private ConvertedAsset resolveCover(InvoiceItem item, VinylPageData page, List<Path> temporaryFiles) {
        if (page == null || blank(page.frontImageUrl())) {
            return new ConvertedAsset(null, null, "Imagen: no se encontro portada descargada para " + itemLabel(item));
        }
        Path localPath = vinylFutureAssetService.localPath(page.frontImageUrl());
        if (localPath == null || !Files.isRegularFile(localPath)) {
            return new ConvertedAsset(null, null, "Imagen: falta archivo local para " + itemLabel(item) + " (" + page.frontImageUrl() + ")");
        }
        try (InputStream input = new BufferedInputStream(Files.newInputStream(localPath))) {
            BufferedImage source = ImageIO.read(input);
            if (source == null) {
                return new ConvertedAsset(null, null, "Imagen: formato no soportado o archivo invalido para " + itemLabel(item));
            }
            BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = rgb.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, null);
            graphics.dispose();

            Path jpg = Files.createTempFile("vinylfuture-cover-", ".jpg");
            temporaryFiles.add(jpg);
            if (!ImageIO.write(rgb, "jpg", jpg.toFile())) {
                Files.deleteIfExists(jpg);
                temporaryFiles.remove(jpg);
                return new ConvertedAsset(null, null, "Imagen: no se pudo convertir a JPG para " + itemLabel(item));
            }
            return new ConvertedAsset(coverFilename(item, page), jpg, null);
        } catch (IOException ex) {
            return new ConvertedAsset(null, null, "Imagen: error preparando portada para " + itemLabel(item) + " (" + ex.getMessage() + ")");
        }
    }

    private ConvertedAsset resolveAudio(InvoiceItem item, TrackInfo track, int index, List<Path> temporaryFiles) {
        String code = trackCode(item);
        String position = firstNonBlank(track.label(), "T" + (index + 1));
        String trackName = firstNonBlank(track.name(), "Unknown Track");
        String targetName = sanitizePath(code) + " - " + sanitizePath(position) + " - " + sanitizePath(trackName) + ".mp3";

        if (track == null || blank(track.mp3Url())) {
            return new ConvertedAsset(targetName, null,
                "Audio: falta preview para " + itemLabel(item) + " [" + position + " - " + trackName + "]");
        }
        Path localPath = vinylFutureAssetService.localPath(track.mp3Url());
        if (localPath == null || !Files.isRegularFile(localPath)) {
            return new ConvertedAsset(targetName, null,
                "Audio: falta archivo local para " + itemLabel(item) + " [" + position + " - " + trackName + "]");
        }
        try {
            if (!isLikelyMp3(localPath)) {
                return new ConvertedAsset(targetName, null,
                    "Audio: el archivo descargado no es MP3 valido para " + itemLabel(item)
                        + " [" + position + " - " + trackName + "]");
            }
            Path mp3 = Files.createTempFile("vinylfuture-audio-", ".mp3");
            temporaryFiles.add(mp3);
            Files.copy(localPath, mp3, StandardCopyOption.REPLACE_EXISTING);
            return new ConvertedAsset(targetName, mp3, null);
        } catch (IOException ex) {
            return new ConvertedAsset(targetName, null,
                "Audio: error preparando track para " + itemLabel(item)
                    + " [" + position + " - " + trackName + "] (" + ex.getMessage() + ")");
        }
    }

    private boolean isLikelyMp3(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) < 3) {
            return false;
        }
        byte[] header = new byte[10];
        try (InputStream input = new BufferedInputStream(Files.newInputStream(file))) {
            int read = input.read(header);
            if (read < 3) {
                return false;
            }
        }
        if (header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
            return true;
        }
        int first = header[0] & 0xFF;
        int second = header[1] & 0xFF;
        return first == 0xFF && (second & 0xE0) == 0xE0;
    }

    private void cleanup(List<Path> files) {
        for (Path file : files) {
            if (file == null) {
                continue;
            }
            try {
                Files.deleteIfExists(file);
            } catch (IOException ex) {
                log.warn("No se pudo eliminar archivo temporal '{}': {}", file, ex.getMessage());
            }
        }
    }

    private void addFileEntry(ZipOutputStream zip, String desiredName, Path file, Set<String> usedNames) throws IOException {
        String name = uniqueZipEntryName(desiredName, usedNames);
        zip.putNextEntry(new ZipEntry(name));
        Files.copy(file, zip);
        zip.closeEntry();
    }

    private void addEntry(ZipOutputStream zip, String desiredName, byte[] data, Set<String> usedNames) throws IOException {
        String name = uniqueZipEntryName(desiredName, usedNames);
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

    private String sanitizeRoot(String value) {
        return sanitizePath(value).replace(' ', '_');
    }

    private String albumFolder(InvoiceItem item, VinylPageData page) {
        return truncate(sanitizePath(trackCode(item, page)), 60)
            + " - "
            + truncate(sanitizePath(firstNonBlank(page != null ? page.title() : null, item.album(), "Unknown Album")), 120);
    }

    private String coverFilename(InvoiceItem item, VinylPageData page) {
        return sanitizePath(trackCode(item, page))
            + " - "
            + sanitizePath(firstNonBlank(page != null ? page.title() : null, item.album(), "Unknown Album"))
            + " - Cover.jpg";
    }

    private String itemLabel(InvoiceItem item) {
        return firstNonBlank(item.codigoCatalogo(), "SIN-CODIGO") + " - " + firstNonBlank(item.album(), "Unknown Album");
    }

    private String trackCode(InvoiceItem item) {
        return firstNonBlank(item.codigoCatalogo(), "UNKNOWN-CODE");
    }

    private String trackCode(InvoiceItem item, VinylPageData page) {
        return firstNonBlank(page != null ? page.code() : null, item.codigoCatalogo(), "UNKNOWN-CODE");
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength).strip();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record ConvertedAsset(String filename, Path path, String failure) {}
}
