package com.sonograma.service;

import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.entity.DiscogsManualBatch;
import com.sonograma.dto.ManualDiscogsExcelRowDTO;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.DiscogsManualBatchStatus;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.repository.DiscoQrCopyRepository;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.DiscogsManualBatchRepository;
import com.sonograma.service.importacion.DiscogsExcelParser;
import com.sonograma.service.importacion.DiscogsLinkParser;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DiscogsManualBatchExcelServiceTest {

    private final DiscogsManualBatchRepository batchRepository = mock(DiscogsManualBatchRepository.class);
    private final DiscoQrCopyRepository copyRepository = mock(DiscoQrCopyRepository.class);
    private final DiscoRepository discoRepository = mock(DiscoRepository.class);
    private final DiscogsManualBatchExcelService service =
            new DiscogsManualBatchExcelService(batchRepository, copyRepository, discoRepository);

    @Test
    void exportsOneRowPerExactCopyWithCanonicalColumnsAndValues() throws Exception {
        DiscogsManualBatch batch = batch(15L, DiscogsManualBatchStatus.FINALIZED);
        String fullUrl = "https://www.discogs.com/es/release/111-ZP-Tracid";
        Disco first = product(10L, fullUrl, "Tech House", "INTERNAL-1", EstadoDisco.VENDIDO);
        Disco second = product(20L, null, "Techno", "INTERNAL-2", EstadoDisco.DISPONIBLE);
        DiscoQrCopy firstAvailable = copy(101L, first, 1, new BigDecimal("937.50"), "VG+", EstadoCopiaDisco.DISPONIBLE);
        DiscoQrCopy firstSold = copy(102L, first, 2, null, "ROTO", EstadoCopiaDisco.VENDIDO);
        DiscoQrCopy secondAvailable = copy(103L, second, 1, new BigDecimal("625"), null, EstadoCopiaDisco.DISPONIBLE);

        when(batchRepository.findById(15L)).thenReturn(Optional.of(batch));
        when(copyRepository.findByManualDiscogsBatchIdOrderByCopyNumber(15L))
                .thenReturn(List.of(firstAvailable, firstSold, secondAvailable));
        when(discoRepository.findAllById(anyList())).thenReturn(List.of(first, second));

        DiscogsManualBatchExcelService.GeneratedWorkbook generated = service.generate(15L);

        assertThat(generated.filename()).isEqualTo("JPH_2026-09-04_batch-15.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(generated.content()))) {
            var sheet = workbook.getSheet("Hoja 1");
            assertThat((Object) sheet).isNotNull();
            assertThat(sheet.getLastRowNum()).isEqualTo(3);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("LINK");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("PRECIO");
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("CONDICION");
            assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("ESTADO");
            assertThat(sheet.getRow(0).getCell(4).getStringCellValue()).isEqualTo("GENERO");
            assertThat(sheet.getRow(0).getCell(5).getStringCellValue()).isEqualTo("CODIGO");

            assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo(fullUrl);
            assertThat(sheet.getRow(1).getCell(0).getHyperlink().getAddress())
                    .isEqualTo(fullUrl);
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("$937,50");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("VG+");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isBlank();
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("Tech House");
            assertThat(sheet.getRow(1).getCell(5).getStringCellValue()).isEqualTo("JPH");

            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("SIN PRECIO");
            assertThat(sheet.getRow(2).getCell(2).getStringCellValue()).isEqualTo("ROTO");
            assertThat(sheet.getRow(2).getCell(3).getStringCellValue()).isEqualTo("VENDIDO");
            assertThat(sheet.getRow(2).getCell(4).getStringCellValue()).isEqualTo("Tech House");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue())
                    .isEqualTo("https://www.discogs.com/release/222-Artist-20-Album-20");
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("$625");
            assertThat(sheet.getRow(3).getCell(4).getStringCellValue()).isEqualTo("Techno");

            XSSFCellStyle headerStyle = (XSSFCellStyle) sheet.getRow(0).getCell(0).getCellStyle();
            assertThat(headerStyle.getFillForegroundColorColor().getRGB())
                    .containsExactly((byte) 0xD9, (byte) 0xD9, (byte) 0xD9);
            assertThat(sheet.getRow(1).getCell(0).getCellStyle().getFontIndexAsInt()).isNotEqualTo(
                    sheet.getRow(1).getCell(1).getCellStyle().getFontIndexAsInt());
            assertThat(sheet.getRow(2).getCell(2).getCellStyle().getFillPattern())
                    .isEqualTo(FillPatternType.NO_FILL);

            DiscogsExcelParser.ParsedSheet parsed = new DiscogsExcelParser(new DiscogsLinkParser()).parse(
                    new MockMultipartFile("file", generated.filename(),
                            DiscogsManualBatchExcelService.XLSX_MEDIA_TYPE, generated.content()));
            assertThat(parsed.rows()).hasSize(3);
            assertThat(parsed.rows().getFirst().discogsId()).isEqualTo(111L);
            assertThat(parsed.rows().getFirst().hyperlinkUrl()).isEqualTo(fullUrl);
        }
    }

    @Test
    void currentBatchExportIncludesOnlyTheRequestedLatestBatch() throws Exception {
        // CURRENT CHARACTERIZATION — EXPECTED TO CHANGE IN PHASE 7 FOR LOGICAL-SOURCE EXPORT.
        // The existing service is deliberately batch-scoped even when multiple technical batches
        // share the same normalized logical source.
        DiscogsManualBatch earlier = batch(21L, DiscogsManualBatchStatus.FINALIZED);
        DiscogsManualBatch latest = batch(22L, DiscogsManualBatchStatus.OPEN);
        Disco earlierProduct = product(10L, null, "House", "EARLIER", EstadoDisco.DISPONIBLE);
        Disco latestProduct = product(20L, null, "Techno", "LATEST", EstadoDisco.DISPONIBLE);
        List<DiscoQrCopy> earlierCopies = List.of(
                copy(201L, earlierProduct, 1, new BigDecimal("100"), "VG", EstadoCopiaDisco.DISPONIBLE),
                copy(202L, earlierProduct, 2, new BigDecimal("110"), "VG+", EstadoCopiaDisco.DISPONIBLE),
                copy(203L, earlierProduct, 3, new BigDecimal("120"), "NM", EstadoCopiaDisco.DISPONIBLE));
        DiscoQrCopy latestCopy = copy(204L, latestProduct, 1, new BigDecimal("130"), "VG+", EstadoCopiaDisco.DISPONIBLE);

        when(batchRepository.findById(22L)).thenReturn(Optional.of(latest));
        when(copyRepository.findByManualDiscogsBatchIdOrderByCopyNumber(22L)).thenReturn(List.of(latestCopy));
        when(discoRepository.findAllById(anyList())).thenReturn(List.of(latestProduct));

        DiscogsManualBatchExcelService.GeneratedWorkbook generated = service.generate(22L);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(generated.content()))) {
            var sheet = workbook.getSheet("Hoja 1");
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("https://www.discogs.com/release/222-Artist-20-Album-20");
        }
        verify(copyRepository).findByManualDiscogsBatchIdOrderByCopyNumber(22L);
        verify(copyRepository, never()).findByManualDiscogsBatchIdOrderByCopyNumber(21L);
        assertThat(earlierCopies).hasSize(3); // Fixture documents the omitted logical-source history.
        assertThat(earlier.getNormalizedCustomerCode()).isEqualTo(latest.getNormalizedCustomerCode());
    }

    @Test
    void twoCopiesOfSameReleaseProduceTwoWorkbookRowsWithinOneBatch() throws Exception {
        DiscogsManualBatch batch = batch(23L, DiscogsManualBatchStatus.OPEN);
        Disco product = product(10L, null, "House", "SAME-RELEASE", EstadoDisco.DISPONIBLE);
        DiscoQrCopy first = copy(301L, product, 1, new BigDecimal("500"), "VG", EstadoCopiaDisco.DISPONIBLE);
        DiscoQrCopy second = copy(302L, product, 2, new BigDecimal("700"), "NM", EstadoCopiaDisco.DISPONIBLE);
        when(batchRepository.findById(23L)).thenReturn(Optional.of(batch));
        when(copyRepository.findByManualDiscogsBatchIdOrderByCopyNumber(23L)).thenReturn(List.of(first, second));
        when(discoRepository.findAllById(anyList())).thenReturn(List.of(product));

        DiscogsManualBatchExcelService.GeneratedWorkbook generated = service.generate(23L);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(generated.content()))) {
            var sheet = workbook.getSheet("Hoja 1");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo(sheet.getRow(2).getCell(0).getStringCellValue());
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("$500");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("$700");
        }
    }

    @Test
    void rejectsInvalidAndEmptyBatches() {
        when(batchRepository.findById(99L)).thenReturn(Optional.of(batch(99L, DiscogsManualBatchStatus.OPEN)));
        when(copyRepository.findByManualDiscogsBatchIdOrderByCopyNumber(99L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.generate(null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("El batch Discogs no es válido.");
        assertThatThrownBy(() -> service.generate(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("El batch Discogs no tiene copias físicas para exportar.");
        assertThatThrownBy(() -> service.generate(100L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Batch Discogs no encontrado con id: 100");
    }

    @Test
    void sourceExportUsesEveryProjectedPhysicalCopyAndKeepsSixExactColumns() throws Exception {
        LocalDateTime firstTime = LocalDateTime.of(2026, 9, 1, 10, 0);
        when(copyRepository.findExcelRowsByManualSource("SV3")).thenReturn(List.of(
                sourceRow(401L, firstTime, "123", "VG+", EstadoCopiaDisco.DISPONIBLE,
                        195695L, "https://www.discogs.com/release/195695", "Rififi",
                        "Dr. Acid And Mr. House", "Acid House"),
                sourceRow(402L, firstTime.plusDays(1), "790", "NM", EstadoCopiaDisco.VENDIDO,
                        195695L, "https://www.discogs.com/master/179057", "Rififi",
                        "Dr. Acid And Mr. House", "Acid House"),
                sourceRow(403L, firstTime.plusDays(2), null, null, EstadoCopiaDisco.REMOVED,
                        300L, "https://example.com/wrong", null, null, null),
                sourceRow(404L, firstTime.plusDays(3), "500.50", "VG", EstadoCopiaDisco.DISPONIBLE,
                        400L, "https://www.discogs.com/release/999-Wrong", "Björk & 東京",
                        "Álbum: Uno!", "Electronic")
        ));

        DiscogsManualBatchExcelService.GeneratedWorkbook generated = service.generateForSource(" sv3 ");

        assertThat(generated.filename()).isEqualTo("SV3_" + LocalDate.now() + ".xlsx");
        assertThat(generated.filename()).doesNotContain("batch-");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(generated.content()))) {
            var sheet = workbook.getSheet("Hoja 1");
            assertThat(sheet.getLastRowNum()).isEqualTo(4);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 6);
            assertThat(java.util.stream.IntStream.range(0, 6)
                    .mapToObj(index -> sheet.getRow(0).getCell(index).getStringCellValue()))
                    .containsExactly("LINK", "PRECIO", "CONDICION", "ESTADO", "GENERO", "CODIGO");

            assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("https://www.discogs.com/release/195695-Rififi-Dr-Acid-And-Mr-House");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("$123");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("VG+");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("DISPONIBLE");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("Acid House");
            assertThat(sheet.getRow(1).getCell(5).getStringCellValue()).isEqualTo("SV3");
            assertThat(sheet.getRow(2).getCell(3).getStringCellValue()).isEqualTo("VENDIDO");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue())
                    .isEqualTo("https://www.discogs.com/release/300");
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("SIN PRECIO");
            assertThat(sheet.getRow(3).getCell(2).getStringCellValue()).isBlank();
            assertThat(sheet.getRow(3).getCell(3).getStringCellValue()).isEqualTo("REMOVED");
            assertThat(sheet.getRow(4).getCell(0).getStringCellValue())
                    .startsWith("https://www.discogs.com/release/400-Bjork-");
        }
        verify(copyRepository).findExcelRowsByManualSource("SV3");
        verifyNoInteractions(batchRepository, discoRepository);
    }

    @Test
    void sourceExportReturnsNotFoundInsteadOfAnotherSourcesData() {
        when(copyRepository.findExcelRowsByManualSource("EMPTY")).thenReturn(List.of());

        assertThatThrownBy(() -> service.generateForSource(" empty "))
                .isInstanceOf(com.sonograma.exception.RecursoNoEncontradoException.class)
                .hasMessage("No hay copias físicas retenidas para la fuente Discogs EMPTY.");
    }

    private DiscogsManualBatch batch(Long id, DiscogsManualBatchStatus status) {
        LocalDateTime started = LocalDateTime.of(2026, 9, 4, 10, 0);
        return DiscogsManualBatch.builder()
                .id(id)
                .customerCode("JPH")
                .normalizedCustomerCode("JPH")
                .status(status)
                .startedAt(started)
                .createdAt(started)
                .updatedAt(started)
                .build();
    }

    private Disco product(Long id, String url, String genre, String internalCode, EstadoDisco status) {
        return Disco.builder()
                .idDisco(id)
                .artista("Artist " + id)
                .album("Album " + id)
                .codigoInterno(internalCode)
                .discogsUrl(url)
                .discogsReleaseId(id == 10L ? 111L : 222L)
                .genero(genre)
                .condicion(CondicionDisco.USADO)
                .estado(status)
                .build();
    }

    private DiscoQrCopy copy(Long id, Disco product, int number, BigDecimal price,
                             String condition, EstadoCopiaDisco status) {
        return DiscoQrCopy.builder()
                .id(id)
                .idDisco(product.getIdDisco())
                .copyNumber(number)
                .codigoQr("qr-" + id)
                .precioVenta(price)
                .condicionFisica(condition)
                .estado(status)
                .build();
    }

    private ManualDiscogsExcelRowDTO sourceRow(
            Long id, LocalDateTime createdAt, String price, String condition, EstadoCopiaDisco state,
            Long releaseId, String storedUrl, String artist, String title, String genre) {
        return new ManualDiscogsExcelRowDTO(
                id, createdAt, price == null ? null : new BigDecimal(price), condition, state,
                "SV3", releaseId, storedUrl, artist, title, genre);
    }
}
