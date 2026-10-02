package com.sonograma.service;

import com.sonograma.dto.ManualDiscogsExpectedCountRequestDTO;
import com.sonograma.dto.ManualDiscogsOperationContextDTO;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.entity.DiscogsManualBatch;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.DiscogsManualBatchStatus;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.TipoDisco;
import com.sonograma.repository.DiscoQrCopyRepository;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.DiscogsManualBatchRepository;
import com.sonograma.repository.ManualDiscogsFinalizationSnapshotRepository;
import com.sonograma.repository.ManualDiscogsSourceReconciliationRepository;
import com.sonograma.service.importacion.ManualDiscogsReceiptOperationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class DiscogsManualSourceExcelIntegrationTest {

    @Autowired private DiscogsManualBatchExcelService excelService;
    @Autowired private ManualDiscogsSourceReconciliationService reconciliationService;
    @Autowired private ManualDiscogsReceiptOperationService operationService;
    @Autowired private DiscogsManualBatchRepository batchRepository;
    @Autowired private DiscoRepository discoRepository;
    @Autowired private DiscoQrCopyRepository copyRepository;
    @Autowired private ManualDiscogsSourceReconciliationRepository reconciliationRepository;
    @Autowired private ManualDiscogsFinalizationSnapshotRepository snapshotRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    void logicalSourceSpansBatchesWithoutCollapsingCopiesOrLeakingSiblingSources() throws Exception {
        DiscogsManualBatch earlier = batch("SV3", DiscogsManualBatchStatus.FINALIZED, 1);
        DiscogsManualBatch later = batch("sv3", DiscogsManualBatchStatus.OPEN, 2);
        DiscogsManualBatch other = batch("LO", DiscogsManualBatchStatus.OPEN, 3);
        Disco rififi = product(195695L, "Rififi", "Dr. Acid And Mr. House", "Acid House", "INTERNAL-RIFIFI");
        Disco second = product(200L, "Second", "Release", "House", "INTERNAL-SECOND");

        copyRepository.saveAll(List.of(
                copy(rififi, earlier, 1, "123", "VG+", EstadoCopiaDisco.DISPONIBLE, 1),
                copy(rififi, earlier, 2, "122", "NM", EstadoCopiaDisco.VENDIDO, 2),
                copy(second, earlier, 1, "121", "VG", EstadoCopiaDisco.REMOVED, 3),
                copy(second, later, 2, "120", "G", EstadoCopiaDisco.DISPONIBLE, 4),
                copy(rififi, other, 3, "999", "M", EstadoCopiaDisco.DISPONIBLE, 5)
        ));

        var pending = operationService.createPending(999001L, 1);
        operationService.updateContext(pending, new ManualDiscogsOperationContextDTO("SV3", null, null));
        var abandoned = operationService.createPending(999002L, 1);
        operationService.updateContext(abandoned, new ManualDiscogsOperationContextDTO("SV3", null, null));
        operationService.abandon(abandoned);

        var expected = reconciliationService.updateExpectedCount(" sv3 ",
                new ManualDiscogsExpectedCountRequestDTO(99, null, "Business expectation", null));
        assertThat(expected.provablePhysicalCopyCount()).isEqualTo(4);

        var sourceWorkbook = excelService.generateForSource(" SV3 ");
        var earlierWorkbook = excelService.generate(earlier.getId());
        var laterWorkbook = excelService.generate(later.getId());

        assertThat(dataRows(sourceWorkbook)).isEqualTo(4);
        assertThat(dataRows(earlierWorkbook)).isEqualTo(3);
        assertThat(dataRows(laterWorkbook)).isEqualTo(1);
        try (XSSFWorkbook workbook = open(sourceWorkbook)) {
            var sheet = workbook.getSheetAt(0);
            assertThat(List.of(
                    sheet.getRow(1).getCell(3).getStringCellValue(),
                    sheet.getRow(2).getCell(3).getStringCellValue(),
                    sheet.getRow(3).getCell(3).getStringCellValue(),
                    sheet.getRow(4).getCell(3).getStringCellValue()))
                    .containsExactly("DISPONIBLE", "VENDIDO", "REMOVED", "DISPONIBLE");
            assertThat(java.util.stream.IntStream.rangeClosed(1, 4)
                    .mapToObj(row -> sheet.getRow(row).getCell(5).getStringCellValue()))
                    .containsOnly("SV3");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("https://www.discogs.com/release/195695-Rififi-Dr-Acid-And-Mr-House");
        }

        assertThat(reconciliationService.current("SV3").provablePhysicalCopyCount())
                .isEqualTo(dataRows(sourceWorkbook));
        assertThat(reconciliationRepository.count()).isEqualTo(1);
        assertThat(snapshotRepository.count()).isZero();
        assertThat(copyRepository.count()).isEqualTo(5);
    }

    @Test
    void expectedNinetyNineAndProvableNinetySevenExportsOnlyNinetySevenRows() throws Exception {
        DiscogsManualBatch first = batch("COUNT97", DiscogsManualBatchStatus.FINALIZED, 10);
        DiscogsManualBatch second = batch("count97", DiscogsManualBatchStatus.OPEN, 11);
        Disco product = product(9700L, "Count", "Evidence", "House", "INTERNAL-97");
        List<DiscoQrCopy> copies = new ArrayList<>();
        for (int index = 1; index <= 97; index++) {
            copies.add(copy(product, index <= 60 ? first : second, index, String.valueOf(500 + index),
                    "VG+", index == 2 ? EstadoCopiaDisco.VENDIDO
                            : index == 3 ? EstadoCopiaDisco.REMOVED : EstadoCopiaDisco.DISPONIBLE, index));
        }
        copyRepository.saveAll(copies);
        reconciliationService.updateExpectedCount("COUNT97",
                new ManualDiscogsExpectedCountRequestDTO(99, null, null, null));
        var before = reconciliationService.current("COUNT97");

        var workbook = excelService.generateForSource(" count97 ");
        var after = reconciliationService.current("COUNT97");

        assertThat(before.provablePhysicalCopyCount()).isEqualTo(97);
        assertThat(before.difference()).isEqualTo(-2);
        assertThat(dataRows(workbook)).isEqualTo(97);
        assertThat(after).isEqualTo(before);
        assertThat(snapshotRepository.count()).isZero();
        assertThat(copyRepository.count()).isEqualTo(97);
    }

    @Test
    void oneHundredCopySourceExportUsesOneBoundedSelect() throws Exception {
        DiscogsManualBatch first = batch("PERF100", DiscogsManualBatchStatus.FINALIZED, 20);
        DiscogsManualBatch second = batch("perf100", DiscogsManualBatchStatus.OPEN, 21);
        Disco firstProduct = product(10001L, "Large", "First", "Techno", "INTERNAL-A");
        Disco secondProduct = product(10002L, "Large", "Second", "House", "INTERNAL-B");
        List<DiscoQrCopy> copies = new ArrayList<>();
        for (int index = 1; index <= 100; index++) {
            Disco product = index <= 50 ? firstProduct : secondProduct;
            copies.add(copy(product, index <= 50 ? first : second,
                    index <= 50 ? index : index - 50, "800", "NM",
                    EstadoCopiaDisco.DISPONIBLE, index));
        }
        copyRepository.saveAll(copies);
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var workbook = excelService.generateForSource("PERF100");

        assertThat(dataRows(workbook)).isEqualTo(100);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    private DiscogsManualBatch batch(String source, DiscogsManualBatchStatus status, int day) {
        LocalDateTime time = LocalDateTime.of(2026, 9, day, 10, 0);
        return batchRepository.save(DiscogsManualBatch.builder()
                .customerCode(source.trim().toUpperCase())
                .normalizedCustomerCode(source.trim().toUpperCase())
                .status(status)
                .startedAt(time)
                .createdAt(time)
                .updatedAt(time)
                .finalizedAt(status == DiscogsManualBatchStatus.FINALIZED ? time.plusHours(1) : null)
                .build());
    }

    private Disco product(Long releaseId, String artist, String title, String genre, String internalCode) {
        return discoRepository.save(Disco.builder()
                .artista(artist)
                .album(title)
                .genero(genre)
                .codigoInterno(internalCode)
                .discogsReleaseId(releaseId)
                .discogsUrl("https://www.discogs.com/release/" + releaseId)
                .condicion(CondicionDisco.USADO)
                .tipoDisco(TipoDisco.VINILO)
                .estado(EstadoDisco.DISPONIBLE)
                .build());
    }

    private DiscoQrCopy copy(Disco product, DiscogsManualBatch batch, int copyNumber,
                             String price, String condition, EstadoCopiaDisco state, int minute) {
        return DiscoQrCopy.builder()
                .idDisco(product.getIdDisco())
                .copyNumber(copyNumber)
                .codigoQr("phase7-" + batch.getId() + "-" + product.getIdDisco() + "-" + copyNumber)
                .manualDiscogsBatch(batch)
                .precioVenta(new BigDecimal(price))
                .condicionFisica(condition)
                .estado(state)
                .createdAt(LocalDateTime.of(2026, 9, 1, 8, 0).plusMinutes(minute))
                .build();
    }

    private int dataRows(DiscogsManualBatchExcelService.GeneratedWorkbook generated) throws Exception {
        try (XSSFWorkbook workbook = open(generated)) {
            return workbook.getSheetAt(0).getLastRowNum();
        }
    }

    private XSSFWorkbook open(DiscogsManualBatchExcelService.GeneratedWorkbook generated) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(generated.content()));
    }
}
