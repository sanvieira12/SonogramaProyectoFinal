package com.sonograma.service.importacion;

import com.sonograma.dto.DiscoImportPreviewDTO;
import com.sonograma.dto.DiscoRequestDTO;
import com.sonograma.dto.DiscoResponseDTO;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.PricingMode;
import com.sonograma.enums.TipoDisco;
import com.sonograma.mapper.DiscoMapper;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.service.CatalogPricingService;
import com.sonograma.service.AudioPreviewService;
import com.sonograma.service.DiscoQrCopyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class VinylFutureImportService {

    private final DiscoRepository discoRepository;
    private final CatalogPricingService catalogPricingService;
    private final DiscoQrCopyService qrCopyService;
    private final AudioPreviewService audioPreviewService;

    private static final Map<String, String> COLUMN_ALIASES = new HashMap<>();

    static {
        COLUMN_ALIASES.put("artista", "artista");
        COLUMN_ALIASES.put("artist", "artista");
        COLUMN_ALIASES.put("album", "album");
        COLUMN_ALIASES.put("álbum", "album");
        COLUMN_ALIASES.put("título", "album");
        COLUMN_ALIASES.put("titulo", "album");
        COLUMN_ALIASES.put("title", "album");
        COLUMN_ALIASES.put("año", "anio");
        COLUMN_ALIASES.put("anio", "anio");
        COLUMN_ALIASES.put("year", "anio");
        COLUMN_ALIASES.put("precio", "precio");
        COLUMN_ALIASES.put("price", "precio");
        COLUMN_ALIASES.put("precio venta", "precio");
        COLUMN_ALIASES.put("costo", "costo");
        COLUMN_ALIASES.put("cost", "costo");
        COLUMN_ALIASES.put("precio compra", "costo");
        COLUMN_ALIASES.put("purchase price", "costo");
        COLUMN_ALIASES.put("condición", "condicion");
        COLUMN_ALIASES.put("condicion", "condicion");
        COLUMN_ALIASES.put("condition", "condicion");
        COLUMN_ALIASES.put("género", "genero");
        COLUMN_ALIASES.put("genero", "genero");
        COLUMN_ALIASES.put("genre", "genero");
        COLUMN_ALIASES.put("sello", "sello");
        COLUMN_ALIASES.put("label", "sello");
        COLUMN_ALIASES.put("sello discográfico", "sello");
        COLUMN_ALIASES.put("catálogo", "catalogo");
        COLUMN_ALIASES.put("catalogo", "catalogo");
        COLUMN_ALIASES.put("catalog", "catalogo");
        COLUMN_ALIASES.put("código", "catalogo");
        COLUMN_ALIASES.put("codigo", "catalogo");
        COLUMN_ALIASES.put("formato", "formato");
        COLUMN_ALIASES.put("format", "formato");
        COLUMN_ALIASES.put("pais", "pais");
        COLUMN_ALIASES.put("país", "pais");
        COLUMN_ALIASES.put("country", "pais");
        COLUMN_ALIASES.put("estilo", "estilo");
        COLUMN_ALIASES.put("style", "estilo");
        COLUMN_ALIASES.put("notas", "notas");
        COLUMN_ALIASES.put("notes", "notas");
        COLUMN_ALIASES.put("observaciones", "notas");
        COLUMN_ALIASES.put("cantidad", "cantidad");
        COLUMN_ALIASES.put("quantity", "cantidad");
        COLUMN_ALIASES.put("stock", "cantidad");
    }

    public List<DiscoImportPreviewDTO> parsearExcel(MultipartFile file) throws IOException {
        List<DiscoImportPreviewDTO> previews = new ArrayList<>();

        try (Workbook workbook = openWorkbook(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return previews;

            Map<Integer, String> columnMap = buildColumnMap(headerRow);
            log.debug("VinylFuture Excel — columnas detectadas: {}", columnMap);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) continue;

                DiscoImportPreviewDTO preview = parsearFila(row, columnMap, i + 1);
                previews.add(preview);
            }
        }

        log.info("VinylFuture Excel parse: {} filas procesadas", previews.size());
        return previews;
    }

    @Transactional
    public List<DiscoResponseDTO> confirmarImport(List<DiscoImportPreviewDTO> seleccionados) {
        List<DiscoResponseDTO> guardados = new ArrayList<>();
        for (DiscoImportPreviewDTO preview : seleccionados) {
            if (!preview.getErrores().isEmpty() && tieneErroresCriticos(preview)) continue;
            try {
                if (preview.getCodigoInterno() != null
                        && !preview.getCodigoInterno().isBlank()
                        && discoRepository.findByCodigoInterno(preview.getCodigoInterno()).isPresent()) {
                    log.info("VinylFuture Excel omitido por código duplicado: {}", preview.getCodigoInterno());
                    continue;
                }
                DiscoRequestDTO req = mapearARequest(preview);
                if (req.getPrecioVenta() == null && req.getCosto() != null) {
                    CatalogPricingService.PricingResult pricing =
                        catalogPricingService.calculate(req.getCosto(), preview.getFormato());
                    if (pricing != null) req.setPrecioVenta(pricing.finalPriceUyu());
                    req.setPricingMode(PricingMode.AUTO);
                } else if (req.getPrecioVenta() != null) {
                    req.setPricingMode(PricingMode.MANUAL);
                }
                com.sonograma.entity.Disco disco = DiscoMapper.toEntity(req);
                disco.setEstado(com.sonograma.enums.EstadoDisco.DISPONIBLE);
                disco.setCodigoQr(UUID.randomUUID().toString());
                disco = discoRepository.save(disco);
                qrCopyService.synchronize(disco);
                audioPreviewService.guardarDesdeTracks(disco.getIdDisco(), preview.getTracks());
                disco = discoRepository.save(disco);
                DiscoResponseDTO dto = DiscoMapper.toDTO(disco);
                dto.setAudioPreviews(audioPreviewService.listarPorDisco(disco.getIdDisco()));
                dto.setQrCopies(qrCopyService.listDtos(disco));
                guardados.add(dto);
            } catch (Exception e) {
                log.warn("Error guardando disco '{}': {}", preview.getAlbum(), e.getMessage());
            }
        }
        return guardados;
    }

    private Map<Integer, String> buildColumnMap(Row headerRow) {
        Map<Integer, String> map = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String header = getCellString(cell).toLowerCase(Locale.ROOT).trim();
            String campo = COLUMN_ALIASES.get(header);
            if (campo != null) {
                map.put(cell.getColumnIndex(), campo);
            }
        }
        return map;
    }

    private DiscoImportPreviewDTO parsearFila(Row row, Map<Integer, String> columnMap, int numeroFila) {
        Map<String, String> valores = new HashMap<>();
        columnMap.forEach((colIdx, campo) -> {
            Cell cell = row.getCell(colIdx);
            if (cell != null) {
                valores.put(campo, getCellString(cell).trim());
            }
        });

        DiscoImportPreviewDTO.DiscoImportPreviewDTOBuilder builder = DiscoImportPreviewDTO.builder();
        List<String> errores = new ArrayList<>();

        String artista = valores.getOrDefault("artista", "").trim();
        String album = valores.getOrDefault("album", "").trim();

        if (artista.isBlank()) errores.add("Artista requerido");
        if (album.isBlank()) errores.add("Álbum requerido");

        builder.artista(artista.isBlank() ? null : artista);
        builder.album(album.isBlank() ? null : album);
        builder.sello(valores.get("sello"));
        builder.genero(valores.get("genero"));
        builder.pais(valores.get("pais"));
        builder.estilo(valores.get("estilo"));
        builder.notas(valores.get("notas"));
        builder.codigoInterno(valores.get("catalogo"));
        builder.formato(valores.get("formato"));
        builder.cantidadCopias(parseQuantity(valores.get("cantidad"), errores));
        builder.estado("DISPONIBLE");
        builder.filaExcel(numeroFila);

        String anioStr = valores.get("anio");
        if (anioStr != null && !anioStr.isBlank()) {
            try {
                builder.anio((int) Double.parseDouble(anioStr));
            } catch (NumberFormatException e) {
                errores.add("Año inválido: " + anioStr);
            }
        }

        String precioStr = valores.get("precio");
        if (precioStr != null && !precioStr.isBlank()) {
            String limpio = precioStr.replaceAll("[^0-9.,]", "").replace(",", ".");
            try {
                builder.precioVenta(new BigDecimal(limpio));
            } catch (NumberFormatException e) {
                errores.add("Precio inválido: " + precioStr);
            }
        }

        String costoStr = valores.get("costo");
        if (costoStr != null && !costoStr.isBlank()) {
            String limpio = costoStr.replaceAll("[^0-9.,]", "").replace(",", ".");
            try {
                builder.costo(new BigDecimal(limpio));
            } catch (NumberFormatException e) {
                errores.add("Costo inválido: " + costoStr);
            }
        }

        String condicionStr = valores.get("condicion");
        if (condicionStr != null && !condicionStr.isBlank()) {
            builder.condicion(mapearCondicion(condicionStr));
        }

        builder.errores(errores);
        return builder.build();
    }

    private String mapearCondicion(String valor) {
        return switch (valor.toUpperCase(Locale.ROOT).trim()) {
            case "NUEVO", "NEW", "M", "MINT" -> CondicionDisco.NUEVO.name();
            case "USADO", "USED", "VG", "VG+", "VG++", "VERY GOOD" -> CondicionDisco.USADO.name();
            case "CONSIGNACION", "CONSIGNACIÓN" -> CondicionDisco.CONSIGNACION.name();
            case "CATALOGO", "CATÁLOGO" -> CondicionDisco.CATALOGO.name();
            default -> valor.toUpperCase(Locale.ROOT);
        };
    }

    private DiscoRequestDTO mapearARequest(DiscoImportPreviewDTO preview) {
        DiscoRequestDTO req = new DiscoRequestDTO();
        req.setArtista(preview.getArtista());
        req.setAlbum(preview.getAlbum());
        req.setSelloDiscografico(preview.getSello());
        req.setGenero(preview.getGenero());
        req.setPais(preview.getPais());
        req.setEstilo(preview.getEstilo());
        req.setNotas(preview.getNotas());
        req.setCodigoInterno(preview.getCodigoInterno());
        req.setAnio(preview.getAnio());
        req.setPrecioVenta(preview.getPrecioVenta());
        req.setCosto(preview.getCosto());
        req.setFormato(preview.getFormato());
        req.setCantidadCopias(preview.getCantidadCopias() != null ? preview.getCantidadCopias() : 1);
        req.setTracklist(preview.getTracklist());
        req.setImagenUrl(preview.getImagenUrl());
        req.setPreviewUrl(preview.getPreviewUrl());
        req.setDiscogsUrl(preview.getDiscogsUrl());
        req.setProcedencia(preview.getProcedencia());

        if (preview.getCondicion() != null) {
            try {
                req.setCondicion(CondicionDisco.valueOf(preview.getCondicion()));
            } catch (IllegalArgumentException e) {
                req.setCondicion(CondicionDisco.USADO);
            }
        } else {
            req.setCondicion(CondicionDisco.USADO);
        }

        if (preview.getFormato() != null) {
            try {
                req.setTipoDisco(TipoDisco.valueOf(preview.getFormato().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                req.setTipoDisco(TipoDisco.VINILO);
            }
        } else {
            req.setTipoDisco(TipoDisco.VINILO);
        }

        return req;
    }

    private Integer parseQuantity(String value, List<String> errores) {
        if (value == null || value.isBlank()) return 1;
        try {
            int quantity = (int) Double.parseDouble(value);
            if (quantity < 0) throw new NumberFormatException();
            return quantity;
        } catch (NumberFormatException ex) {
            errores.add("Cantidad inválida: " + value);
            return 1;
        }
    }

    private boolean tieneErroresCriticos(DiscoImportPreviewDTO preview) {
        return preview.getArtista() == null || preview.getAlbum() == null;
    }

    private boolean isRowEmpty(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String val = getCellString(cell).trim();
                if (!val.isEmpty()) return false;
            }
        }
        return true;
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield String.valueOf((int) cell.getNumericCellValue());
                }
                double d = cell.getNumericCellValue();
                yield d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield String.valueOf(cell.getNumericCellValue()); }
                catch (Exception e) { yield cell.getStringCellValue(); }
            }
            default -> "";
        };
    }

    private Workbook openWorkbook(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase().endsWith(".xls")) {
            return new HSSFWorkbook(file.getInputStream());
        }
        return new XSSFWorkbook(file.getInputStream());
    }
}
