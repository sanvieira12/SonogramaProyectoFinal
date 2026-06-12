package com.sonograma.service;

import com.sonograma.dto.ClienteImportResultDTO;
import com.sonograma.entity.Cliente;
import com.sonograma.repository.ClienteRepository;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
@ActiveProfiles("dev")
class ClienteExcelImportServiceTest {

    @Autowired
    private ClienteExcelImportService importService;

    @Autowired
    private ClienteRepository clienteRepository;

    @BeforeEach
    void limpiarClientes() {
        clienteRepository.deleteAll();
    }

    @Test
    void importaLaEstructuraRealIgnoraFilasVaciasYPersisteLosCampos() throws Exception {
        MockMultipartFile file = excel(
                new ClientRow(" Ismael Gonzalez ", 46410184d, 99860877d,
                        " Balneario Buenos Aires ", " Retira en agencia ",
                        null, null, LocalDate.of(2026, 3, 15)),
                new ClientRow("Cliente sin email", 51575632d, 95442045d,
                        "Canelones ciudad", "Retira en agencia",
                        "", " Prefiere contacto telefónico ", LocalDate.of(2026, 4, 28))
        );

        ClienteImportResultDTO result = importService.importar(file, null);

        assertThat(result.getHoja()).isEqualTo("Hoja 1");
        assertThat(result.getTotalFilasLeidas()).isEqualTo(2);
        assertThat(result.getClientesValidos()).isEqualTo(2);
        assertThat(result.getCreados()).isEqualTo(2);
        assertThat(result.getActualizados()).isZero();
        assertThat(result.getOmitidos()).isZero();
        assertThat(result.getFilas()).extracting("filaExcel").containsExactly(3, 4);

        Cliente ismael = clienteRepository.findByCedulaAndActivoTrue("46410184").orElseThrow();
        assertThat(ismael.getNombre()).isEqualTo("Ismael");
        assertThat(ismael.getApellido()).isEqualTo("Gonzalez");
        assertThat(ismael.getTelefono()).isEqualTo("99860877");
        assertThat(ismael.getLocalidad()).isEqualTo("Balneario Buenos Aires");
        assertThat(ismael.getDireccion()).isEqualTo("Retira en agencia");
        assertThat(ismael.getEmail()).isNull();
        assertThat(ismael.getUltimaCompra()).isEqualTo(LocalDate.of(2026, 3, 15));

        Cliente sinEmail = clienteRepository.findByCedulaAndActivoTrue("51575632").orElseThrow();
        assertThat(sinEmail.getEmail()).isNull();
        assertThat(sinEmail.getObservaciones()).isEqualTo("Prefiere contacto telefónico");
    }

    @Test
    void actualizaPorCedulaEnLugarDeDuplicar() throws Exception {
        Cliente existente = new Cliente();
        existente.setNombre("Ismael");
        existente.setCedula("46410184");
        existente.setTelefono("099000000");
        existente.setActivo(true);
        clienteRepository.saveAndFlush(existente);

        ClienteImportResultDTO result = importService.importar(excel(
                new ClientRow("Ismael Gonzalez", 46410184d, 99860877d,
                        "Balneario Buenos Aires", "Retira en agencia",
                        null, null, LocalDate.of(2026, 3, 15))
        ), null);

        assertThat(result.getCreados()).isZero();
        assertThat(result.getActualizados()).isEqualTo(1);
        assertThat(clienteRepository.count()).isEqualTo(1);
        Cliente actualizado = clienteRepository.findByCedulaAndActivoTrue("46410184").orElseThrow();
        assertThat(actualizado.getNombre()).isEqualTo("Ismael");
        assertThat(actualizado.getApellido()).isEqualTo("Gonzalez");
        assertThat(actualizado.getTelefono()).isEqualTo("99860877");
        assertThat(actualizado.getUltimaCompra()).isEqualTo(LocalDate.of(2026, 3, 15));
    }

    @Test
    void importaElArchivoRealCuandoSeProporcionaComoFixture() throws Exception {
        String fixture = System.getProperty("cliente.fixture");
        assumeTrue(fixture != null && Files.exists(Path.of(fixture)));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                Path.of(fixture).getFileName().toString(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                Files.readAllBytes(Path.of(fixture))
        );

        ClienteImportResultDTO result = importService.importar(file, null);

        assertThat(result.getHoja()).isEqualTo("Hoja 1");
        assertThat(result.getTotalFilasLeidas()).isEqualTo(8);
        assertThat(result.getClientesValidos()).isEqualTo(8);
        Cliente ismael = clienteRepository.findByCedulaAndActivoTrue("46410184").orElseThrow();
        assertThat(ismael.getTelefono()).isEqualTo("99860877");
        assertThat(ismael.getEmail()).isNull();
        assertThat(ismael.getUltimaCompra()).isEqualTo(LocalDate.of(2026, 3, 15));
    }

    private MockMultipartFile excel(ClientRow... clients) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Hoja 1");
            Row header = sheet.createRow(0);
            String[] headers = {
                    "CLIENTE", "CEDULA", "TELEFONO", "LOCALIDAD",
                    "DIRECCION", "EMAIL", "INFORMACION ADICIONAL", "ULTIMA COMPRA"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            sheet.createRow(1);

            CreationHelper helper = workbook.getCreationHelper();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("dd/mm/yyyy"));
            for (int i = 0; i < clients.length; i++) {
                ClientRow client = clients[i];
                Row row = sheet.createRow(i + 2);
                row.createCell(0).setCellValue(client.nombre());
                row.createCell(1).setCellValue(client.cedula());
                row.createCell(2).setCellValue(client.telefono());
                row.createCell(3).setCellValue(client.localidad());
                row.createCell(4).setCellValue(client.direccion());
                if (client.email() != null) row.createCell(5).setCellValue(client.email());
                if (client.notas() != null) row.createCell(6).setCellValue(client.notas());
                if (client.ultimaCompra() != null) {
                    var dateCell = row.createCell(7);
                    Date date = Date.from(client.ultimaCompra().atStartOfDay(ZoneId.systemDefault()).toInstant());
                    dateCell.setCellValue(date);
                    dateCell.setCellStyle(dateStyle);
                }
            }
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "INFORMACION DE CIENTES.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }

    private record ClientRow(
            String nombre,
            double cedula,
            double telefono,
            String localidad,
            String direccion,
            String email,
            String notas,
            LocalDate ultimaCompra
    ) {}
}
