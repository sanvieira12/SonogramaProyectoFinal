package com.sonograma.service;

import com.sonograma.dto.DeudorImportResultDTO;
import com.sonograma.dto.DeudorImportRowDTO;
import com.sonograma.entity.Cliente;
import com.sonograma.entity.Deudor;
import com.sonograma.exception.NegocioException;
import com.sonograma.repository.ClienteRepository;
import com.sonograma.repository.DeudorRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeudorExcelImportService {

    private static final DateTimeFormatter FMT_ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter FMT_DMY = DateTimeFormatter.ofPattern("d/M/uuuu");

    private final DeudorRepository deudorRepository;
    private final ClienteRepository clienteRepository;

    @Transactional
    public DeudorImportResultDTO importarExcel(MultipartFile file) {
        validarArchivo(file);

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new NegocioException("El archivo Excel no contiene hojas");
            }
            Sheet sheet = workbook.getSheetAt(0);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            Map<String, Long> clienteIndex = buildClienteIndex();

            List<DeudorImportRowDTO> filas = new ArrayList<>();
            int totalFilas = 0;
            int filasVacias = 0;
            int creados = 0;
            int actualizados = 0;
            int omitidos = 0;
            int errores = 0;

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);

                String nombre = textoCell(row, 0, evaluator);
                String montoRaw = textoCell(row, 1, evaluator);
                String notasRaw = textoCell(row, 3, evaluator);
                String discosRaw = textoCell(row, 4, evaluator);

                if (esBlancoONull(nombre) && esBlancoONull(montoRaw)
                        && esBlancoONull(notasRaw) && esBlancoONull(discosRaw)) {
                    filasVacias++;
                    continue;
                }
                totalFilas++;

                if (esBlancoONull(nombre)) {
                    errores++;
                    filas.add(DeudorImportRowDTO.builder()
                            .fila(i + 1)
                            .estado("ERROR")
                            .mensaje("Nombre de deudor vacío")
                            .build());
                    continue;
                }

                BigDecimal montoUyu = null;
                if (!esBlancoONull(montoRaw)) {
                    try {
                        montoUyu = parsearMonto(montoRaw);
                    } catch (Exception ignored) {
                        // montoUyu stays null
                    }
                }

                LocalDate fechaEstimada = null;
                try {
                    fechaEstimada = parsearFecha(row, 2, evaluator);
                } catch (Exception ignored) {
                    // fechaEstimada stays null
                }

                Long idCliente = resolverCliente(nombre, clienteIndex);

                Optional<Deudor> existente = deudorRepository
                        .findByNombreDeudorIgnoreCaseAndMontoOriginalAndFechaEstimada(
                                nombre, montoRaw, fechaEstimada);

                String rowEstado;
                String rowMensaje;

                if (existente.isPresent()) {
                    Deudor d = existente.get();
                    d.setMontoUyu(montoUyu);
                    d.setNotas(notasRaw);
                    d.setDescripcionDiscos(discosRaw);
                    if (idCliente != null) d.setIdCliente(idCliente);
                    d.setUpdatedAt(LocalDateTime.now());
                    deudorRepository.save(d);
                    actualizados++;
                    rowEstado = "UPDATED";
                    rowMensaje = "Deudor actualizado";
                } else {
                    Deudor d = new Deudor();
                    d.setNombreDeudor(nombre);
                    d.setIdCliente(idCliente);
                    d.setMontoOriginal(montoRaw);
                    d.setMontoUyu(montoUyu);
                    d.setFechaEstimada(fechaEstimada);
                    d.setNotas(notasRaw);
                    d.setDescripcionDiscos(discosRaw);
                    d.setEstado("PENDIENTE");
                    d.setFuente("IMPORTACION_EXCEL");
                    d.setCreatedAt(LocalDateTime.now());
                    d.setUpdatedAt(LocalDateTime.now());
                    deudorRepository.save(d);
                    creados++;
                    rowEstado = "CREATED";
                    rowMensaje = "Deudor creado";
                }

                filas.add(DeudorImportRowDTO.builder()
                        .fila(i + 1)
                        .nombreDeudor(nombre)
                        .montoOriginal(montoRaw)
                        .montoUyu(montoUyu)
                        .fechaEstimada(fechaEstimada)
                        .notas(notasRaw)
                        .descripcionDiscos(discosRaw)
                        .estado(rowEstado)
                        .mensaje(rowMensaje)
                        .build());
            }

            deudorRepository.flush();

            return DeudorImportResultDTO.builder()
                    .totalFilas(totalFilas)
                    .filasVacias(filasVacias)
                    .detectados(totalFilas)
                    .creados(creados)
                    .actualizados(actualizados)
                    .omitidos(omitidos)
                    .errores(errores)
                    .filas(filas)
                    .build();

        } catch (NegocioException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new NegocioException("No se pudo importar el Excel: " + mensajeEx(ex));
        }
    }

    private void validarArchivo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new NegocioException("El archivo Excel está vacío");
        }
        String nombre = file.getOriginalFilename();
        if (nombre != null && !nombre.toLowerCase(Locale.ROOT).matches(".*\\.xls[x]?$")) {
            throw new NegocioException("Formato no válido. Subí un archivo .xlsx o .xls");
        }
    }

    private Map<String, Long> buildClienteIndex() {
        return clienteRepository.findAll().stream()
                .collect(Collectors.toMap(
                        c -> normalizar((c.getNombre() != null ? c.getNombre() : "")
                                + " " + (c.getApellido() != null ? c.getApellido() : "")),
                        Cliente::getIdCliente,
                        (a, b) -> a
                ));
    }

    private Long resolverCliente(String nombreDeudor, Map<String, Long> index) {
        return index.get(normalizar(nombreDeudor));
    }

    private BigDecimal parsearMonto(String raw) {
        String cleaned = raw.replaceAll("[$\\s]", "");
        if (cleaned.contains("+")) {
            String[] parts = cleaned.split("\\+");
            int sum = 0;
            for (String p : parts) {
                sum += Integer.parseInt(p.trim());
            }
            return BigDecimal.valueOf(sum);
        }
        return new BigDecimal(cleaned);
    }

    private LocalDate parsearFecha(Row row, int col, FormulaEvaluator evaluator) {
        if (row == null) return null;
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        CellType type = tipoCelda(cell, evaluator);
        if (type == CellType.BLANK) return null;
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String value = textoCell(row, col, evaluator);
        if (esBlancoONull(value)) return null;
        try { return LocalDate.parse(value, FMT_ISO); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(value, FMT_DMY); } catch (DateTimeParseException ignored) {}
        return null;
    }

    private String textoCell(Row row, int col, FormulaEvaluator evaluator) {
        if (row == null) return null;
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        CellType type = tipoCelda(cell, evaluator);
        String value = switch (type) {
            case STRING -> valorString(cell, evaluator).trim();
            case NUMERIC -> BigDecimal.valueOf(valorNumerico(cell, evaluator))
                    .stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
        return esBlancoONull(value) ? null : value;
    }

    private CellType tipoCelda(Cell cell, FormulaEvaluator evaluator) {
        return cell.getCellType() == CellType.FORMULA
                ? evaluator.evaluateFormulaCell(cell)
                : cell.getCellType();
    }

    private String valorString(Cell cell, FormulaEvaluator evaluator) {
        return cell.getCellType() == CellType.FORMULA
                ? evaluator.evaluate(cell).getStringValue()
                : cell.getStringCellValue();
    }

    private double valorNumerico(Cell cell, FormulaEvaluator evaluator) {
        return cell.getCellType() == CellType.FORMULA
                ? evaluator.evaluate(cell).getNumberValue()
                : cell.getNumericCellValue();
    }

    private String normalizar(String value) {
        if (value == null) return "";
        String nfd = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{M}", "").replaceAll("\\s+", " ").trim();
    }

    private boolean esBlancoONull(String value) {
        return value == null || value.isBlank();
    }

    private String mensajeEx(Exception ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? ex.getClass().getSimpleName()
                : ex.getMessage();
    }
}
