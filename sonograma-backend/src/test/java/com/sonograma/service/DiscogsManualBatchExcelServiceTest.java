package com.sonograma.service;

import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.entity.DiscogsManualBatch;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.DiscogsManualBatchStatus;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.repository.DiscoQrCopyRepository;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.DiscogsManualBatchRepository;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
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
        Disco first = product(10L, "https://www.discogs.com/release/111", "House", "INTERNAL-1", EstadoDisco.VENDIDO);
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
            assertThat(sheet.getRow(0).getCell(5).getStringCellValue()).isEqualTo("CODIGO ");

            assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("https://www.discogs.com/release/111");
            assertThat(sheet.getRow(1).getCell(0).getHyperlink().getAddress())
                    .isEqualTo("https://www.discogs.com/release/111");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("$937,50");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("VG+");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isBlank();
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("House");
            assertThat(sheet.getRow(1).getCell(5).getStringCellValue()).isEqualTo("JPH");

            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("SIN PRECIO");
            assertThat(sheet.getRow(2).getCell(2).getStringCellValue()).isEqualTo("ROTO");
            assertThat(sheet.getRow(2).getCell(3).getStringCellValue()).isEqualTo("VENDIDO");
            assertThat(sheet.getRow(2).getCell(4).getStringCellValue()).isEqualTo("House");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue())
                    .isEqualTo("https://www.discogs.com/release/222");
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("$625");
            assertThat(sheet.getRow(3).getCell(4).getStringCellValue()).isEqualTo("Techno");

            XSSFCellStyle headerStyle = (XSSFCellStyle) sheet.getRow(0).getCell(0).getCellStyle();
            assertThat(headerStyle.getFillForegroundColorColor().getRGB())
                    .containsExactly((byte) 0xD9, (byte) 0xD9, (byte) 0xD9);
            assertThat(sheet.getRow(1).getCell(0).getCellStyle().getFontIndexAsInt()).isNotEqualTo(
                    sheet.getRow(1).getCell(1).getCellStyle().getFontIndexAsInt());
            assertThat(sheet.getRow(2).getCell(2).getCellStyle().getFillPattern())
                    .isEqualTo(FillPatternType.NO_FILL);
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
}
