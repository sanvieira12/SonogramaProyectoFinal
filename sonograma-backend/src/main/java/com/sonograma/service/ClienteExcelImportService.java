package com.sonograma.service;

import com.sonograma.dto.ClienteImportResultDTO;
import com.sonograma.dto.ClienteImportRowDTO;
import com.sonograma.entity.Cliente;
import com.sonograma.exception.NegocioException;
import com.sonograma.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ClienteExcelImportService {

    private static final Set<String> IDENTITY_HEADERS = Set.of("cliente", "cedula", "telefono");
    private static final Map<String, String> HEADER_ALIASES = Map.ofEntries(
            Map.entry("cliente", "cliente"),
            Map.entry("nombre", "cliente"),
            Map.entry("cedula", "cedula"),
            Map.entry("documento", "cedula"),
            Map.entry("telefono", "telefono"),
            Map.entry("celular", "telefono"),
            Map.entry("localidad", "localidad"),
            Map.entry("direccion", "direccion"),
            Map.entry("email", "email"),
            Map.entry("correo", "email"),
            Map.entry("informacion adicional", "informacion_adicional"),
            Map.entry("observaciones", "informacion_adicional"),
            Map.entry("notas", "informacion_adicional"),
            Map.entry("ultima compra", "ultima_compra")
    );
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("d.M.uuuu")
    );

    private final ClienteRepository clienteRepository;

    @Transactional
    public ClienteImportResultDTO importar(MultipartFile file, String nombreHoja) {
        validarArchivo(file);

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = seleccionarHoja(workbook, nombreHoja);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            HeaderInfo header = detectarEncabezado(sheet, evaluator);
            List<Cliente> clientes = new ArrayList<>(clienteRepository.findAll());
            List<ClienteImportRowDTO> reporte = new ArrayList<>();
            int total = 0;
            int validos = 0;
            int creados = 0;
            int actualizados = 0;
            int omitidos = 0;
            int errores = 0;
            int incidencias = 0;

            for (int index = header.rowIndex() + 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (filaVacia(row, evaluator)) {
                    continue;
                }
                total++;

                try {
                    ParsedClient parsed = parsearFila(row, header.columns(), evaluator);

                    if (vacio(parsed.nombre())) {
                        omitidos++;
                        reporte.add(reporte(row, parsed, "skipped", "Se requiere CLIENTE o NOMBRE"));
                        continue;
                    }
                    validos++;

                    Optional<Cliente> existente = buscarCoincidencia(clientes, parsed);
                    if (existente.isPresent()) {
                        actualizar(existente.get(), parsed);
                        clienteRepository.save(existente.get());
                        actualizados++;
                        reporte.add(reporte(row, parsed, "updated", "Cliente existente actualizado"));
                    } else {
                        Cliente nuevo = crear(parsed);
                        clienteRepository.save(nuevo);
                        clientes.add(nuevo);
                        creados++;
                        reporte.add(reporte(row, parsed, "created", "Cliente creado"));
                    }
                } catch (Exception ex) {
                    errores++;
                    incidencias++;
                    reporte.add(reporteError(row, ex.getMessage()));
                }
            }

            clienteRepository.flush();
            return ClienteImportResultDTO.builder()
                    .hoja(sheet.getSheetName())
                    .totalFilasLeidas(total)
                    .clientesValidos(validos)
                    .creados(creados)
                    .actualizados(actualizados)
                    .omitidos(omitidos)
                    .errores(errores)
                    .filasConIncidencias(incidencias)
                    .filas(reporte)
                    .build();
        } catch (NegocioException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new NegocioException("No se pudo importar el Excel: " + mensaje(ex));
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

    private Sheet seleccionarHoja(Workbook workbook, String nombreHoja) {
        if (workbook.getNumberOfSheets() == 0) {
            throw new NegocioException("El archivo Excel no contiene hojas");
        }
        if (nombreHoja == null || nombreHoja.isBlank()) {
            return workbook.getSheetAt(0);
        }
        Sheet sheet = workbook.getSheet(nombreHoja.trim());
        if (sheet == null) {
            throw new NegocioException("No existe la hoja '" + nombreHoja.trim() + "'");
        }
        return sheet;
    }

    private HeaderInfo detectarEncabezado(Sheet sheet, FormulaEvaluator evaluator) {
        int limite = Math.min(sheet.getLastRowNum(), 50);
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= limite; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            Map<String, Integer> columns = new LinkedHashMap<>();
            for (Cell cell : row) {
                String normalized = normalizarEncabezado(valorTexto(cell, evaluator));
                String canonical = HEADER_ALIASES.get(normalized);
                if (canonical != null) {
                    columns.putIfAbsent(canonical, cell.getColumnIndex());
                }
            }
            if (columns.keySet().stream().anyMatch(IDENTITY_HEADERS::contains)) {
                return new HeaderInfo(rowIndex, columns);
            }
        }
        throw new NegocioException(
                "No se encontraron encabezados válidos. El archivo debe incluir CLIENTE, CEDULA o TELEFONO");
    }

    private ParsedClient parsearFila(Row row, Map<String, Integer> columns, FormulaEvaluator evaluator) {
        String nombreCompleto = texto(row, columns.get("cliente"), evaluator);
        String nombre = null;
        String apellido = null;
        if (nombreCompleto != null) {
            int spaceIdx = nombreCompleto.indexOf(' ');
            if (spaceIdx > 0) {
                nombre = nombreCompleto.substring(0, spaceIdx);
                apellido = nombreCompleto.substring(spaceIdx + 1).trim();
                if (apellido.isBlank()) apellido = null;
            } else {
                nombre = nombreCompleto;
            }
        }
        String cedula = identificador(row, columns.get("cedula"), evaluator);
        String telefono = identificador(row, columns.get("telefono"), evaluator);
        String localidad = texto(row, columns.get("localidad"), evaluator);
        String direccion = texto(row, columns.get("direccion"), evaluator);
        String email = texto(row, columns.get("email"), evaluator);
        String notas = texto(row, columns.get("informacion_adicional"), evaluator);
        LocalDate ultimaCompra = fecha(row, columns.get("ultima_compra"), evaluator);
        return new ParsedClient(nombre, apellido, cedula, telefono, localidad, direccion, email, notas, ultimaCompra);
    }

    private Optional<Cliente> buscarCoincidencia(List<Cliente> clientes, ParsedClient parsed) {
        if (!vacio(parsed.cedula())) {
            Optional<Cliente> match = clientes.stream()
                    .filter(c -> normalizarIdentificador(c.getCedula()).equals(normalizarIdentificador(parsed.cedula())))
                    .findFirst();
            if (match.isPresent()) return match;
        }
        if (!vacio(parsed.telefono())) {
            Optional<Cliente> match = clientes.stream()
                    .filter(c -> normalizarIdentificador(c.getTelefono())
                            .equals(normalizarIdentificador(parsed.telefono())))
                    .findFirst();
            if (match.isPresent()) return match;
        }
        if (!vacio(parsed.nombre())) {
            return clientes.stream()
                    .filter(c -> normalizarTexto(c.getNombre()).equals(normalizarTexto(parsed.nombre())))
                    .findFirst();
        }
        return Optional.empty();
    }

    private Cliente crear(ParsedClient parsed) {
        Cliente cliente = new Cliente();
        cliente.setNombre(parsed.nombre());
        cliente.setActivo(true);
        actualizar(cliente, parsed);
        return cliente;
    }

    private void actualizar(Cliente cliente, ParsedClient parsed) {
        if (!vacio(parsed.nombre())) cliente.setNombre(parsed.nombre());
        if (!vacio(parsed.apellido())) cliente.setApellido(parsed.apellido());
        if (!vacio(parsed.cedula())) cliente.setCedula(parsed.cedula());
        if (!vacio(parsed.telefono())) cliente.setTelefono(parsed.telefono());
        if (!vacio(parsed.localidad())) cliente.setLocalidad(parsed.localidad());
        if (!vacio(parsed.direccion())) cliente.setDireccion(parsed.direccion());
        if (!vacio(parsed.email())) cliente.setEmail(parsed.email());
        if (!vacio(parsed.notas())) cliente.setObservaciones(parsed.notas());
        if (parsed.ultimaCompra() != null) cliente.setUltimaCompra(parsed.ultimaCompra());
        cliente.setActivo(true);
    }

    private ClienteImportRowDTO reporte(Row row, ParsedClient parsed, String estado, String mensaje) {
        String nombreCompleto = null;
        if (parsed != null && parsed.nombre() != null) {
            nombreCompleto = parsed.apellido() != null
                    ? parsed.nombre() + " " + parsed.apellido()
                    : parsed.nombre();
        }
        return ClienteImportRowDTO.builder()
                .filaExcel(row.getRowNum() + 1)
                .nombre(nombreCompleto)
                .cedula(parsed != null ? parsed.cedula() : null)
                .telefono(parsed != null ? parsed.telefono() : null)
                .estado(estado)
                .mensaje(mensaje)
                .build();
    }

    private ClienteImportRowDTO reporteError(Row row, String mensajeError) {
        return ClienteImportRowDTO.builder()
                .filaExcel(row.getRowNum() + 1)
                .estado("error")
                .mensaje(mensajeError)
                .build();
    }

    private boolean filaVacia(Row row, FormulaEvaluator evaluator) {
        if (row == null) return true;
        for (Cell cell : row) {
            if (!vacio(valorTexto(cell, evaluator))) return false;
        }
        return true;
    }

    private String texto(Row row, Integer column, FormulaEvaluator evaluator) {
        if (column == null || row == null) return null;
        return nuloSiVacio(valorTexto(row.getCell(column), evaluator));
    }

    private String identificador(Row row, Integer column, FormulaEvaluator evaluator) {
        if (column == null || row == null) return null;
        Cell cell = row.getCell(column);
        if (cell == null) return null;
        CellType type = tipoCelda(cell, evaluator);
        if (type == CellType.NUMERIC) {
            double value = valorNumerico(cell, evaluator);
            return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        }
        String value = nuloSiVacio(valorTexto(cell, evaluator));
        return value == null ? null : value.replaceFirst("\\.0+$", "");
    }

    private LocalDate fecha(Row row, Integer column, FormulaEvaluator evaluator) {
        if (column == null || row == null) return null;
        Cell cell = row.getCell(column);
        if (cell == null || tipoCelda(cell, evaluator) == CellType.BLANK) return null;
        if (tipoCelda(cell, evaluator) == CellType.NUMERIC) {
            double numeric = valorNumerico(cell, evaluator);
            if (DateUtil.isValidExcelDate(numeric)) {
                return DateUtil.getJavaDate(numeric)
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
            }
        }
        String value = nuloSiVacio(valorTexto(cell, evaluator));
        if (value == null) return null;
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Try next format.
            }
        }
        return null;
    }

    private String valorTexto(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return "";
        CellType type = tipoCelda(cell, evaluator);
        return switch (type) {
            case STRING -> valorString(cell, evaluator).trim();
            case NUMERIC -> BigDecimal.valueOf(valorNumerico(cell, evaluator))
                    .stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private CellType tipoCelda(Cell cell, FormulaEvaluator evaluator) {
        return cell.getCellType() == CellType.FORMULA
                ? evaluator.evaluateFormulaCell(cell)
                : cell.getCellType();
    }

    private String valorString(Cell cell, FormulaEvaluator evaluator) {
        if (cell.getCellType() == CellType.FORMULA) {
            return evaluator.evaluate(cell).getStringValue();
        }
        return cell.getStringCellValue();
    }

    private double valorNumerico(Cell cell, FormulaEvaluator evaluator) {
        if (cell.getCellType() == CellType.FORMULA) {
            return evaluator.evaluate(cell).getNumberValue();
        }
        return cell.getNumericCellValue();
    }

    private String normalizarEncabezado(String value) {
        return normalizarTexto(value).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private String normalizarTexto(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "");
    }

    private String normalizarIdentificador(String value) {
        return normalizarTexto(value).replaceAll("[^a-z0-9]", "");
    }

    private String nuloSiVacio(String value) {
        return vacio(value) ? null : value.trim();
    }

    private boolean vacio(String value) {
        return value == null || value.isBlank();
    }

    private String mensaje(Exception ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? ex.getClass().getSimpleName()
                : ex.getMessage();
    }

    private record HeaderInfo(int rowIndex, Map<String, Integer> columns) {}

    private record ParsedClient(
            String nombre,
            String apellido,
            String cedula,
            String telefono,
            String localidad,
            String direccion,
            String email,
            String notas,
            LocalDate ultimaCompra
    ) {}
}
