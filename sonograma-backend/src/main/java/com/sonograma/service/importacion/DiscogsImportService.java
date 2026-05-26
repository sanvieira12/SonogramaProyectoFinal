package com.sonograma.service.importacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonograma.dto.DiscoImportPreviewDTO;
import com.sonograma.dto.DiscoRequestDTO;
import com.sonograma.dto.DiscoResponseDTO;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.TipoDisco;
import com.sonograma.mapper.DiscoMapper;
import com.sonograma.repository.DiscoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscogsImportService {

    private static final String DISCOGS_BASE = "https://api.discogs.com";
    private static final String USER_AGENT = "SonogramaApp/1.0 +https://github.com/sanvieira12/SonogramaProyectoFinal";
    private static final Pattern RELEASE_ID_PATTERN = Pattern.compile("discogs\\.com/(?:.*?/)?releases?/(\\d+)");
    private static final int REQUEST_DELAY_MS = 300;

    @Value("${discogs.api.token:}")
    private String discogsToken;

    private final DiscoRepository discoRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public DiscoImportPreviewDTO fetchDesdeLink(String url) {
        String releaseId = extraerReleaseId(url);
        if (releaseId == null) {
            return DiscoImportPreviewDTO.builder()
                    .errores(List.of("No se pudo extraer el ID de release de la URL: " + url))
                    .build();
        }
        return fetchRelease(releaseId, url);
    }

    public List<DiscoImportPreviewDTO> fetchDesdeExcel(MultipartFile file) throws IOException {
        List<String> urls = extraerUrlsDeExcel(file);
        List<DiscoImportPreviewDTO> resultados = new ArrayList<>();

        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            log.info("Discogs fetch {}/{}: {}", i + 1, urls.size(), url);
            resultados.add(fetchDesdeLink(url));
            if (i < urls.size() - 1) {
                try { Thread.sleep(REQUEST_DELAY_MS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        }
        return resultados;
    }

    @Transactional
    public DiscoResponseDTO guardar(DiscoImportPreviewDTO preview) {
        DiscoRequestDTO req = mapearARequest(preview);
        com.sonograma.entity.Disco disco = DiscoMapper.toEntity(req);
        disco.setEstado(com.sonograma.enums.EstadoDisco.DISPONIBLE);
        disco.setCodigoQr(UUID.randomUUID().toString());
        return DiscoMapper.toDTO(discoRepository.save(disco));
    }

    @Transactional
    public List<DiscoResponseDTO> guardarLote(List<DiscoImportPreviewDTO> previews) {
        List<DiscoResponseDTO> guardados = new ArrayList<>();
        for (DiscoImportPreviewDTO preview : previews) {
            if (!preview.getErrores().isEmpty()) continue;
            try {
                guardados.add(guardar(preview));
            } catch (Exception e) {
                log.warn("Error guardando disco '{}': {}", preview.getAlbum(), e.getMessage());
            }
        }
        return guardados;
    }

    private DiscoImportPreviewDTO fetchRelease(String releaseId, String originalUrl) {
        try {
            String apiUrl = DISCOGS_BASE + "/releases/" + releaseId;
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .GET();

            if (discogsToken != null && !discogsToken.isBlank()) {
                reqBuilder.header("Authorization", "Discogs token=" + discogsToken);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return errorPreview("Discogs devolvió HTTP " + response.statusCode() + " para release " + releaseId);
            }

            JsonNode json = objectMapper.readTree(response.body());
            return mapearRelease(json, releaseId, originalUrl);

        } catch (Exception e) {
            log.warn("Error fetching Discogs release {}: {}", releaseId, e.getMessage());
            return errorPreview("Error al consultar Discogs: " + e.getMessage());
        }
    }

    private DiscoImportPreviewDTO mapearRelease(JsonNode json, String releaseId, String originalUrl) {
        DiscoImportPreviewDTO.DiscoImportPreviewDTOBuilder builder = DiscoImportPreviewDTO.builder();

        String artista = extraerArtista(json);
        String album = json.path("title").asText(null);
        String sello = extraerSello(json);
        int anio = json.path("year").asInt(0);
        String pais = json.path("country").asText(null);
        String genero = extraerPrimero(json.path("genres"));
        String estilo = extraerPrimero(json.path("styles"));
        String formato = extraerFormato(json);
        String imagenUrl = extraerImagenUrl(json);
        String previewUrl = extraerPreviewUrl(json);
        String tracklist = extraerTracklist(json);
        String codigoInterno = generarCodigo(artista, anio, releaseId);

        builder.artista(artista)
                .album(album)
                .sello(sello)
                .anio(anio > 0 ? anio : null)
                .pais(pais)
                .genero(genero)
                .estilo(estilo)
                .formato(formato)
                .imagenUrl(imagenUrl)
                .previewUrl(previewUrl)
                .tracklist(tracklist)
                .codigoInterno(codigoInterno)
                .discogsUrl(originalUrl)
                .estado("DISPONIBLE")
                .condicion(CondicionDisco.USADO.name())
                .errores(new ArrayList<>());

        return builder.build();
    }

    private String extraerArtista(JsonNode json) {
        JsonNode artists = json.path("artists");
        if (artists.isArray() && !artists.isEmpty()) {
            return artists.get(0).path("name").asText(null);
        }
        return null;
    }

    private String extraerSello(JsonNode json) {
        JsonNode labels = json.path("labels");
        if (labels.isArray() && !labels.isEmpty()) {
            return labels.get(0).path("name").asText(null);
        }
        return null;
    }

    private String extraerPrimero(JsonNode array) {
        if (array.isArray() && !array.isEmpty()) {
            return array.get(0).asText(null);
        }
        return null;
    }

    private String extraerFormato(JsonNode json) {
        JsonNode formats = json.path("formats");
        if (formats.isArray() && !formats.isEmpty()) {
            String name = formats.get(0).path("name").asText("").toUpperCase(Locale.ROOT);
            if (name.contains("VINYL") || name.contains("LP") || name.contains("EP")) return TipoDisco.VINILO.name();
            if (name.contains("CD")) return TipoDisco.CD.name();
            if (name.contains("CASSETTE")) return TipoDisco.CASSETTE.name();
        }
        return TipoDisco.VINILO.name();
    }

    private String extraerImagenUrl(JsonNode json) {
        JsonNode images = json.path("images");
        if (images.isArray()) {
            for (JsonNode img : images) {
                if ("primary".equals(img.path("type").asText())) {
                    return img.path("uri").asText(null);
                }
            }
            if (!images.isEmpty()) {
                return images.get(0).path("uri").asText(null);
            }
        }
        return null;
    }

    private String extraerPreviewUrl(JsonNode json) {
        JsonNode videos = json.path("videos");
        if (videos.isArray() && !videos.isEmpty()) {
            return videos.get(0).path("uri").asText(null);
        }
        return null;
    }

    private String extraerTracklist(JsonNode json) {
        JsonNode tracklist = json.path("tracklist");
        if (!tracklist.isArray() || tracklist.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (JsonNode track : tracklist) {
            String pos = track.path("position").asText("");
            String title = track.path("title").asText("");
            if (!pos.isBlank() || !title.isBlank()) {
                if (!sb.isEmpty()) sb.append("\n");
                if (!pos.isBlank()) sb.append(pos).append(". ");
                sb.append(title);
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private String generarCodigo(String artista, Integer anio, String releaseId) {
        String initials = artista != null
                ? Arrays.stream(artista.split("\\s+"))
                        .map(w -> w.substring(0, 1).toUpperCase(Locale.ROOT))
                        .reduce("", String::concat)
                : "XX";
        String year = anio != null ? String.valueOf(anio) : "0000";
        return initials + "-" + year + "-" + releaseId;
    }

    private String extraerReleaseId(String url) {
        if (url == null) return null;
        Matcher m = RELEASE_ID_PATTERN.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private List<String> extraerUrlsDeExcel(MultipartFile file) throws IOException {
        List<String> urls = new ArrayList<>();
        try (Workbook workbook = openWorkbook(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) return urls;

            int urlCol = -1;
            for (Cell cell : header) {
                String text = getCellString(cell).toLowerCase(Locale.ROOT).trim();
                if (text.contains("link") || text.contains("url") || text.contains("discogs")) {
                    urlCol = cell.getColumnIndex();
                    break;
                }
            }
            if (urlCol == -1) return urls;

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Cell cell = row.getCell(urlCol);
                String val = getCellString(cell).trim();
                if (!val.isBlank() && val.contains("discogs.com")) {
                    urls.add(val);
                }
            }
        }
        return urls;
    }

    private DiscoRequestDTO mapearARequest(DiscoImportPreviewDTO preview) {
        DiscoRequestDTO req = new DiscoRequestDTO();
        req.setArtista(preview.getArtista());
        req.setAlbum(preview.getAlbum());
        req.setSelloDiscografico(preview.getSello());
        req.setGenero(preview.getGenero());
        req.setPais(preview.getPais());
        req.setEstilo(preview.getEstilo());
        req.setAnio(preview.getAnio());
        req.setPrecioVenta(preview.getPrecioVenta());
        req.setCosto(preview.getCosto());
        req.setTracklist(preview.getTracklist());
        req.setImagenUrl(preview.getImagenUrl());
        req.setPreviewUrl(preview.getPreviewUrl());
        req.setDiscogsUrl(preview.getDiscogsUrl());
        req.setCodigoInterno(preview.getCodigoInterno());
        req.setNotas(preview.getNotas());
        req.setProcedencia(preview.getProcedencia());

        if (preview.getCondicion() != null) {
            try { req.setCondicion(CondicionDisco.valueOf(preview.getCondicion())); }
            catch (IllegalArgumentException e) { req.setCondicion(CondicionDisco.USADO); }
        } else {
            req.setCondicion(CondicionDisco.USADO);
        }

        if (preview.getFormato() != null) {
            try { req.setTipoDisco(TipoDisco.valueOf(preview.getFormato().toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException e) { req.setTipoDisco(TipoDisco.VINILO); }
        } else {
            req.setTipoDisco(TipoDisco.VINILO);
        }

        return req;
    }

    private DiscoImportPreviewDTO errorPreview(String mensaje) {
        return DiscoImportPreviewDTO.builder().errores(List.of(mensaje)).build();
    }

    private Workbook openWorkbook(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase().endsWith(".xls")) {
            return new HSSFWorkbook(file.getInputStream());
        }
        return new XSSFWorkbook(file.getInputStream());
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                yield d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }
}
