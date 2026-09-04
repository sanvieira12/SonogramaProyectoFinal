package com.sonograma.service;

import com.sonograma.dto.AudioPreviewDTO;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.entity.DiscogsManualBatch;
import com.sonograma.repository.DiscoQrCopyRepository;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.DiscogsManualBatchRepository;
import com.sonograma.service.importacion.DiscogsCoverService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Generates a reusable ZIP from the exact physical copies of one manual Discogs batch. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiscogsManualBatchZipService {

    public static final String ZIP_MEDIA_TYPE = "application/zip";

    private static final String FILE_DATE_PATTERN = "yyyy-MM-dd";
    private static final String SUMMARY_NAME = "discogs-summary.csv";
    private static final String ERRORS_NAME = "errors.csv";

    private final DiscogsManualBatchRepository batchRepository;
    private final DiscoQrCopyRepository copyRepository;
    private final DiscoRepository discoRepository;
    private final DiscogsCoverService coverService;
    private final QRService qrService;
    private final AudioPreviewService audioPreviewService;

    public GeneratedZip generate(Long batchId) {
        if (batchId == null || batchId <= 0) {
            throw new com.sonograma.exception.NegocioException("El batch Discogs no es válido.");
        }
        DiscogsManualBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new com.sonograma.exception.RecursoNoEncontradoException(
                        "Batch Discogs", batchId));
        List<DiscoQrCopy> copies = copyRepository.findByManualDiscogsBatchIdOrderByCopyNumber(batchId);
        if (copies.isEmpty()) {
            throw new com.sonograma.exception.NegocioException(
                    "El batch Discogs no tiene copias físicas para exportar.");
        }

        List<Long> productIds = copies.stream()
                .map(DiscoQrCopy::getIdDisco)
                .distinct()
                .toList();
        Map<Long, Disco> products = new HashMap<>();
        discoRepository.findAllById(productIds).forEach(product -> products.put(product.getIdDisco(), product));
        for (DiscoQrCopy copy : copies) {
            if (!products.containsKey(copy.getIdDisco())) {
                throw new com.sonograma.exception.NegocioException(
                        "No se encontró el producto de la copia " + copy.getId() + ".");
            }
        }

        LinkedHashMap<Long, Disco> productsInOrder = new LinkedHashMap<>();
        copies.forEach(copy -> productsInOrder.putIfAbsent(copy.getIdDisco(), products.get(copy.getIdDisco())));
        List<ZipWarning> warnings = new ArrayList<>();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            Set<String> usedNames = new HashSet<>();
            addEntry(zip, SUMMARY_NAME, summaryCsv(batch, copies, products, productsInOrder), usedNames);

            for (Disco product : productsInOrder.values()) {
                String releaseFolder = releaseFolder(product);
                addEntry(zip, releaseFolder + "/release.json", releaseJson(product), usedNames);
                addEntry(zip, releaseFolder + "/tracks-and-links.txt",
                        tracksAndLinks(product), usedNames);

                DiscogsCoverService.CoverResult cover = coverService.existing(product.getImagenUrl());
                if (cover.available() && cover.localPath() != null && Files.isRegularFile(cover.localPath())) {
                    addFileEntry(zip, releaseFolder + "/cover" + extensionWithDot(cover.localPath()),
                            cover.localPath(), usedNames);
                } else {
                    warnings.add(new ZipWarning(product, "MISSING_LOCAL_FILE",
                            "La portada persistida no está disponible en el almacenamiento local."));
                }
            }

            for (DiscoQrCopy copy : copies) {
                Disco product = products.get(copy.getIdDisco());
                byte[] qr = qrService.generarQRParaCopia(product, copy);
                String copyId = copy.getId() == null ? "row-" + (copies.indexOf(copy) + 1)
                        : "copy-" + copy.getId();
                String entry = "qr/" + copyId + "-disco-" + product.getIdDisco()
                        + "-copy-" + copy.getCopyNumber() + ".png";
                addEntry(zip, entry, qr, usedNames);
            }

            if (!warnings.isEmpty()) {
                addEntry(zip, ERRORS_NAME, errorsCsv(warnings), usedNames);
            }
            zip.finish();
            return new GeneratedZip(output.toByteArray(), filename(batch));
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el ZIP del batch Discogs.", ex);
        }
    }

    private String summaryCsv(DiscogsManualBatch batch, List<DiscoQrCopy> copies,
                              Map<Long, Disco> products, Map<Long, Disco> productsInOrder) {
        Map<Long, String> coverStatuses = new HashMap<>();
        for (Disco product : productsInOrder.values()) {
            DiscogsCoverService.CoverResult cover = coverService.existing(product.getImagenUrl());
            coverStatuses.put(product.getIdDisco(), cover.available() ? "AVAILABLE" : "MISSING_LOCAL");
        }
        StringBuilder csv = new StringBuilder();
        csv.append("copy_id,copy_number,disco_id,discogs_release_id,discogs_url,artist,title,price_uyu,")
                .append("condition,status,genre,customer_code,qr_code,cover_status\n");
        for (DiscoQrCopy copy : copies) {
            Disco product = products.get(copy.getIdDisco());
            csv.append(csv(copy.getId())).append(',')
                    .append(csv(copy.getCopyNumber())).append(',')
                    .append(csv(product.getIdDisco())).append(',')
                    .append(csv(product.getDiscogsReleaseId())).append(',')
                    .append(csv(product.getDiscogsUrl())).append(',')
                    .append(csv(product.getArtista())).append(',')
                    .append(csv(product.getAlbum())).append(',')
                    .append(csv(copy.getPrecioVenta())).append(',')
                    .append(csv(copy.getCondicionFisica())).append(',')
                    .append(csv(copy.getEstado())).append(',')
                    .append(csv(product.getGenero())).append(',')
                    .append(csv(batch.getNormalizedCustomerCode())).append(',')
                    .append(csv(copy.getCodigoQr())).append(',')
                    .append(csv(coverStatuses.get(product.getIdDisco())))
                    .append('\n');
        }
        return csv.toString();
    }

    private String errorsCsv(List<ZipWarning> warnings) {
        StringBuilder csv = new StringBuilder("disco_id,discogs_release_id,artist,title,error_code,error_message\n");
        Set<Long> reported = new HashSet<>();
        for (ZipWarning warning : warnings) {
            if (!reported.add(warning.product().getIdDisco())) continue;
            Disco product = warning.product();
            csv.append(csv(product.getIdDisco())).append(',')
                    .append(csv(product.getDiscogsReleaseId())).append(',')
                    .append(csv(product.getArtista())).append(',')
                    .append(csv(product.getAlbum())).append(',')
                    .append(csv(warning.code())).append(',')
                    .append(csv(warning.message())).append('\n');
        }
        return csv.toString();
    }

    private String releaseFolder(Disco product) {
        String release = product.getDiscogsReleaseId() == null
                ? "catalog-" + product.getIdDisco()
                : "release-" + product.getDiscogsReleaseId();
        return "releases/" + sanitize(release + "-disco-" + product.getIdDisco());
    }

    private String releaseJson(Disco product) {
        return "{\n"
                + "  \"discogsReleaseId\": " + nullableNumber(product.getDiscogsReleaseId()) + ",\n"
                + "  \"discogsUrl\": \"" + json(product.getDiscogsUrl()) + "\",\n"
                + "  \"artist\": \"" + json(product.getArtista()) + "\",\n"
                + "  \"title\": \"" + json(product.getAlbum()) + "\",\n"
                + "  \"year\": " + nullableNumber(product.getAnio()) + ",\n"
                + "  \"label\": \"" + json(product.getSelloDiscografico()) + "\",\n"
                + "  \"format\": \"" + json(product.getFormato()) + "\",\n"
                + "  \"genre\": \"" + json(product.getGenero()) + "\",\n"
                + "  \"style\": \"" + json(product.getEstilo()) + "\"\n"
                + "}\n";
    }

    private String tracksAndLinks(Disco product) {
        StringBuilder text = new StringBuilder("Tracklist\n")
                .append(product.getTracklist() == null ? "" : product.getTracklist())
                .append("\n\nLinks\n");
        if (product.getDiscogsUrl() != null) text.append("Discogs: ").append(product.getDiscogsUrl()).append('\n');
        if (product.getPreviewUrl() != null) text.append("Preview: ").append(product.getPreviewUrl()).append('\n');
        for (AudioPreviewDTO preview : audioPreviewService.listarPorDisco(product.getIdDisco())) {
            text.append(nullToEmpty(preview.trackPosition())).append(' ')
                    .append(nullToEmpty(preview.trackName()));
            if (preview.youtubeUrl() != null) text.append(" | YouTube: ").append(preview.youtubeUrl());
            if (preview.audioUrl() != null) text.append(" | Preview: ").append(preview.audioUrl());
            text.append('\n');
        }
        return text.toString();
    }

    private void addFileEntry(ZipOutputStream zip, String desiredName, Path file,
                              Set<String> usedNames) throws IOException {
        zip.putNextEntry(new ZipEntry(uniqueName(desiredName, usedNames)));
        Files.copy(file, zip);
        zip.closeEntry();
    }

    private void addEntry(ZipOutputStream zip, String desiredName, String content,
                          Set<String> usedNames) throws IOException {
        addEntry(zip, desiredName, content.getBytes(StandardCharsets.UTF_8), usedNames);
    }

    private void addEntry(ZipOutputStream zip, String desiredName, byte[] content,
                          Set<String> usedNames) throws IOException {
        zip.putNextEntry(new ZipEntry(uniqueName(desiredName, usedNames)));
        zip.write(content);
        zip.closeEntry();
    }

    private String uniqueName(String desiredName, Set<String> usedNames) {
        String[] segments = desiredName.split("/");
        StringBuilder path = new StringBuilder();
        for (String segment : segments) {
            if (segment.isBlank()) continue;
            if (path.length() > 0) path.append('/');
            path.append(sanitize(segment));
        }
        String sanitized = path.isEmpty() ? "archivo" : path.toString();
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

    private String sanitize(String value) {
        String sanitized = value == null ? "archivo" : value
                .replaceAll("[/\\\\:*?\"<>|]", "_")
                .replace("..", "_")
                .strip();
        return sanitized.isBlank() ? "archivo" : sanitized;
    }

    private String csv(Object value) {
        if (value == null) return "";
        String text = value instanceof java.math.BigDecimal decimal
                ? decimal.stripTrailingZeros().toPlainString()
                : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private String nullableNumber(Number value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private String json(String value) {
        return nullToEmpty(value).replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String extensionWithDot(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : ".jpg";
    }

    private String filename(DiscogsManualBatch batch) {
        String customer = sanitize(batch.getNormalizedCustomerCode());
        LocalDateTime timestamp = batch.getStartedAt() != null ? batch.getStartedAt() : batch.getCreatedAt();
        LocalDate date = timestamp == null ? LocalDate.now() : timestamp.toLocalDate();
        return customer + "_" + date.format(DateTimeFormatter.ofPattern(FILE_DATE_PATTERN, Locale.ROOT))
                + "_batch-" + batch.getId() + ".zip";
    }

    public record GeneratedZip(byte[] content, String filename) {}

    private record ZipWarning(Disco product, String code, String message) {}
}
