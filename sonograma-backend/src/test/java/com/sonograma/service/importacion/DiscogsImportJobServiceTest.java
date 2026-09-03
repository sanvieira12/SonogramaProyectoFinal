package com.sonograma.service.importacion;

import com.sonograma.dto.DiscogsImportJobDTO;
import com.sonograma.dto.DiscogsImportRowDTO;
import com.sonograma.dto.DiscogsZipStatusDTO;
import com.sonograma.dto.TrackInfo;
import com.sonograma.entity.DiscogsImportJob;
import com.sonograma.entity.DiscogsImportRow;
import com.sonograma.entity.Disco;
import com.sonograma.enums.DiscogsCatalogImportStatus;
import com.sonograma.enums.DiscogsCoverStatus;
import com.sonograma.enums.DiscogsImportJobStatus;
import com.sonograma.enums.DiscogsImportRowStatus;
import com.sonograma.enums.DiscogsMetadataStatus;
import com.sonograma.enums.DiscogsYoutubeStatus;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.DiscoQrCopyRepository;
import com.sonograma.repository.DiscogsImportJobRepository;
import com.sonograma.repository.DiscogsImportRowRepository;
import com.sonograma.service.AudioPreviewService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("dev")
class DiscogsImportJobServiceTest {

    private static final Path COVERS_DIRECTORY = Path.of(
            System.getProperty("java.io.tmpdir"), "sonograma-discogs-job-test-" + UUID.randomUUID());

    @DynamicPropertySource
    static void discogsCoverDirectory(DynamicPropertyRegistry registry) {
        registry.add("discogs.covers.directory", COVERS_DIRECTORY::toString);
    }

    @Autowired
    private DiscogsImportJobService service;

    @Autowired
    private DiscogsImportRowRepository rowRepository;

    @Autowired
    private DiscogsImportJobRepository jobRepository;

    @Autowired
    private DiscoRepository discoRepository;

    @Autowired
    private DiscoQrCopyRepository qrCopyRepository;

    @Autowired
    private DiscogsCoverService coverService;

    @MockBean
    private DiscogsApiClient apiClient;

    @MockBean
    private AudioPreviewService audioPreviewService;

    @BeforeEach
    void clean() {
        reset(apiClient);
        coverService.clearStoredCovers();
        rowRepository.deleteAll();
        jobRepository.deleteAll();
        discoRepository.deleteAll();
    }

    @Test
    void rateLimitKeepsTheJobAndRowsOutOfCatalogueUntilRetry() throws Exception {
        when(apiClient.newSession()).thenReturn(new DiscogsApiClient.ImportSession());
        when(apiClient.fetch(any(DiscogsApiClient.ImportSession.class), anyString(), anyLong()))
                .thenReturn(DiscogsApiClient.FetchResult.failure(true, 1, "HTTP 429"));

        DiscogsImportJobDTO created = service.createJob(fixture());
        assertThat(created.getTotalRowsRead()).isEqualTo(1);
        assertThat(created.getValidReleaseUrls()).isEqualTo(1);

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            DiscogsImportJobDTO current = service.getJob(created.getId());
            assertThat(current.getStatus()).isEqualTo("completed_with_warnings");
            assertThat(current.getRateLimited()).isEqualTo(1);
            assertThat(current.getRows()).singleElement().satisfies(row -> {
                assertThat(row.getStatus()).isEqualTo("pending_retry");
                assertThat(row.getRetryCount()).isEqualTo(1);
                assertThat(row.getDiscogsId()).isEqualTo(999L);
                assertThat(row.getErrorMessage()).isNull();
                assertThat(row.getWarningMessage()).contains("RATE_LIMITED", "pendiente");
            });
        });

        DiscogsImportJobDTO imported = service.importParsedRows(created.getId());
        assertThat(imported.getImported()).isZero();
        assertThat(imported.getRows()).singleElement().satisfies(row -> {
            assertThat(row.getImportedCatalogProductId()).isNull();
            assertThat(row.getWarningMessage()).contains("RATE_LIMITED");
        });
        assertThat(discoRepository.findAll()).isEmpty();
    }

    @Test
    void reuploadingTheSameWorkbookCreatesAndProcessesANewJob() throws Exception {
        when(apiClient.newSession()).thenReturn(new DiscogsApiClient.ImportSession());
        when(apiClient.fetch(any(DiscogsApiClient.ImportSession.class), anyString(), anyLong()))
                .thenReturn(successResult());

        MockMultipartFile workbook = fixture();
        DiscogsImportJobDTO first = service.createJob(workbook);
        DiscogsImportJobDTO second = service.createJob(new MockMultipartFile(
                "file", workbook.getOriginalFilename(), workbook.getContentType(), workbook.getBytes()
        ));

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(jobRepository.count()).isEqualTo(2);

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            assertThat(service.getJob(first.getId()).getMetadataFetched()).isEqualTo(1);
            assertThat(service.getJob(second.getId()).getMetadataFetched()).isEqualTo(1);
        });
    }

    @Test
    void retryAllPendingMetadataThenImportsAvailableUsedRecordWithOneQr() throws Exception {
        when(apiClient.newSession()).thenReturn(new DiscogsApiClient.ImportSession());
        when(apiClient.fetch(any(DiscogsApiClient.ImportSession.class), anyString(), anyLong()))
                .thenReturn(DiscogsApiClient.FetchResult.failure(true, 1, "HTTP 429"))
                .thenReturn(successResult());

        DiscogsImportJobDTO created = service.createJob(fixture());

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() ->
                assertThat(service.getJob(created.getId()).getMetadataPending()).isEqualTo(1));

        service.retryPendingRows(created.getId());

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            DiscogsImportJobDTO current = service.getJob(created.getId());
            assertThat(current.getStatus()).isEqualTo("completed_with_warnings");
            assertThat(current.getReadyToImport()).isEqualTo(1);
            assertThat(current.getMetadataFetched()).isEqualTo(1);
        });

        DiscogsImportJobDTO imported = service.importParsedRows(created.getId());

        assertThat(imported.getImported()).isEqualTo(1);
        assertThat(imported.getQrEntriesCreated()).isEqualTo(1);
        assertThat(imported.getRows()).singleElement().satisfies(row -> {
            assertThat(row.getImportedCatalogProductId()).isNotNull();
            assertThat(row.getStatus()).isEqualTo("imported");
        });
    }

    @Test
    void soldAndNewExcelValuesStillImportAsUsedWithWarnings() throws Exception {
        when(apiClient.newSession()).thenReturn(new DiscogsApiClient.ImportSession());
        when(apiClient.fetch(any(DiscogsApiClient.ImportSession.class), anyString(), anyLong()))
                .thenReturn(successResult(777L));

        DiscogsImportJobDTO created;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Discogs");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Discogs URL");
            header.createCell(1).setCellValue("Artista");
            header.createCell(2).setCellValue("Album");
            header.createCell(3).setCellValue("Estado");
            header.createCell(4).setCellValue("Condición");
            header.createCell(5).setCellValue("Precio");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("https://discogs.com/release/777");
            row.createCell(1).setCellValue("Excel Artist");
            row.createCell(2).setCellValue("Excel Album");
            row.createCell(3).setCellValue("VENDIDO");
            row.createCell(4).setCellValue("NM");
            row.createCell(5).setCellValue("SP");
            workbook.write(output);
            created = service.createJob(new MockMultipartFile(
                    "file", "sold.xlsx", "application/xlsx", output.toByteArray()
            ));
        }

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            DiscogsImportJobDTO current = service.getJob(created.getId());
            assertThat(current.getReadyToImport()).isEqualTo(1);
            assertThat(current.getSoldRows()).isEqualTo(1);
            assertThat(current.getRows()).singleElement().satisfies(row -> {
                assertThat(row.getStatus()).isEqualTo("parsed");
                assertThat(row.getCatalogImportStatus()).isEqualTo("ready");
                assertThat(row.getWarningMessage()).contains("PRICE_REQUIRES_REVIEW");
                assertThat(row.getCoverErrorCode()).isEqualTo("COVER_UNAVAILABLE");
            });
        });

        DiscogsImportJobDTO imported = service.importParsedRows(created.getId());
        assertThat(imported.getRowsImported()).isEqualTo(1);
        assertThat(discoRepository.findAll()).singleElement().satisfies(disco -> {
            assertThat(disco.getCondicion().name()).isEqualTo("USADO");
            assertThat(disco.getCondicionFisica()).isEqualTo("NM");
            assertThat(disco.getPrecioVenta()).isNull();
            assertThat(disco.getNotas()).contains("Condición física Excel: NM", "Estado Excel: VENDIDO");
        });
    }

    @Test
    void masterResolutionFailureIsPreciseAndDoesNotStopTheFollowingRelease() throws Exception {
        when(apiClient.newSession()).thenReturn(new DiscogsApiClient.ImportSession());
        when(apiClient.fetch(any(DiscogsApiClient.ImportSession.class), anyString(), anyLong()))
                .thenAnswer(invocation -> {
                    String type = invocation.getArgument(1);
                    long id = invocation.getArgument(2);
                    return "master".equals(type)
                            ? DiscogsApiClient.FetchResult.failure(false, 0, "Master sin release principal")
                            : successResult(id);
                });

        DiscogsImportJobDTO created = service.createJob(workbookWithUrls(List.of(
                "https://discogs.com/master/100",
                "https://discogs.com/release/200"
        )));

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            DiscogsImportJobDTO current = service.getJob(created.getId());
            assertThat(current.getStatus()).isEqualTo("completed_with_warnings");
            assertThat(current.getMetadataFetched()).isEqualTo(1);
            assertThat(current.getMetadataFailed()).isEqualTo(1);
            assertThat(current.getRows()).satisfiesExactly(
                    master -> {
                        assertThat(master.getDiscogsType()).isEqualTo("master");
                        assertThat(master.getDiscogsId()).isEqualTo(100L);
                        assertThat(master.getResolvedReleaseId()).isNull();
                        assertThat(master.getMetadataErrorCode()).isEqualTo("MASTER_RESOLUTION_FAILED");
                        assertThat(master.getCatalogImportStatus()).isEqualTo("manual_review");
                        assertThat(master.getWarningMessage()).contains("MASTER_RESOLUTION_REVIEW_REQUIRED");
                    },
                    release -> {
                        assertThat(release.getDiscogsId()).isEqualTo(200L);
                        assertThat(release.getResolvedReleaseId()).isEqualTo(200L);
                        assertThat(release.getMetadataStatus()).isEqualTo("success");
                        assertThat(release.getCatalogImportStatus()).isEqualTo("ready");
                    });
        });
    }

    @Test
    void missingLinkRemainsVisibleAndDoesNotStopTheFollowingRow() throws Exception {
        when(apiClient.newSession()).thenReturn(new DiscogsApiClient.ImportSession());
        when(apiClient.fetch(any(DiscogsApiClient.ImportSession.class), anyString(), anyLong()))
                .thenReturn(successResult(321L));

        DiscogsImportJobDTO created;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Links");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("LINK DE DISCOGS");
            header.createCell(1).setCellValue("ARTISTA");
            header.createCell(2).setCellValue("PRECIO");
            var missing = sheet.createRow(1);
            missing.createCell(1).setCellValue("Nina tastswi");
            missing.createCell(2).setCellValue("SP");
            sheet.createRow(2).createCell(0).setCellValue("https://discogs.com/release/321");
            sheet.createRow(3).createCell(2).setCellValue("SP");
            workbook.write(output);
            created = service.createJob(new MockMultipartFile(
                    "file", "missing-link.xlsx", "application/xlsx", output.toByteArray()
            ));
        }

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            DiscogsImportJobDTO current = service.getJob(created.getId());
            assertThat(current.getStatus()).isEqualTo("completed_with_warnings");
            assertThat(current.getTotalRowsRead()).isEqualTo(3);
            assertThat(current.getLinksDetected()).isEqualTo(1);
            assertThat(current.getMissingDiscogsLinks()).isEqualTo(2);
            assertThat(current.getMetadataFetched()).isEqualTo(1);
            assertThat(current.getRows()).satisfiesExactly(
                    missing -> {
                        assertThat(missing.getArtist()).isEqualTo("Nina tastswi");
                        assertThat(missing.getRawPrice()).isEqualTo("SP");
                        assertThat(missing.getMetadataStatus()).isEqualTo("missing_link");
                        assertThat(missing.getMetadataErrorCode()).isEqualTo("MISSING_DISCOGS_LINK");
                        // Parser status remains compatible, but Phase 2's
                        // metadata gate makes this row ineligible for receipt.
                        assertThat(missing.getCatalogImportStatus()).isEqualTo("ready");
                        assertThat(missing.getWarningMessage()).contains("MISSING_DISCOGS_LINK");
                    },
                    enriched -> assertThat(enriched.getMetadataStatus()).isEqualTo("success"),
                    impossible -> {
                        assertThat(impossible.getCatalogImportStatus()).isEqualTo("manual_review");
                        assertThat(impossible.getWarningMessage()).contains("MANUAL_REVIEW_REQUIRED");
                    });
        });

        DiscogsImportJobDTO imported = service.importParsedRows(created.getId());
        assertThat(imported.getRowsImported()).isEqualTo(1);
        assertThat(imported.getRowsTechnicallyImpossible()).isEqualTo(1);
        assertThat(discoRepository.findAll()).hasSize(1).allSatisfy(disco ->
                assertThat(disco.getCondicion().name()).isEqualTo("USADO"));
    }

    @Test
    void sameWorkbookUploadedAsNewJobReceivesAnotherPhysicalCopy() throws Exception {
        when(apiClient.newSession()).thenReturn(new DiscogsApiClient.ImportSession());
        when(apiClient.fetch(any(DiscogsApiClient.ImportSession.class), anyString(), anyLong()))
                .thenReturn(successResult());

        MockMultipartFile workbook = fixture();
        DiscogsImportJobDTO first = service.createJob(workbook);
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() ->
                assertThat(service.getJob(first.getId()).getReadyToImport()).isEqualTo(1));
        service.importParsedRows(first.getId());

        DiscogsImportJobDTO second = service.createJob(new MockMultipartFile(
                "file", workbook.getOriginalFilename(), workbook.getContentType(), workbook.getBytes()
        ));
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() ->
                assertThat(service.getJob(second.getId()).getReadyToImport()).isEqualTo(1));

        DiscogsImportJobDTO importedAgain = service.importParsedRows(second.getId());

        assertThat(importedAgain.getImported()).isEqualTo(1);
        assertThat(importedAgain.getAlreadyImported()).isZero();
        assertThat(importedAgain.getNewProducts()).isZero();
        assertThat(importedAgain.getExistingProducts()).isEqualTo(1);
        assertThat(importedAgain.getRows()).singleElement().satisfies(row -> {
            assertThat(row.getStatus()).isEqualTo("imported");
            assertThat(row.getCatalogImportStatus()).isEqualTo("imported");
            assertThat(row.getCatalogProductResult()).isEqualTo("EXISTING_PRODUCT");
            assertThat(row.getImportedCatalogProductId()).isNotNull();
        });
        assertThat(discoRepository.findAll()).singleElement()
                .satisfies(disco -> {
                    assertThat(disco.getCantidadCopias()).isEqualTo(2);
                    assertThat(qrCopyRepository.findByIdDiscoOrderByCopyNumber(disco.getIdDisco())).hasSize(2);
                });
    }

    @Test
    void singleLetterBusinessCodeDoesNotMergeDifferentDiscogsReleases() {
        DiscogsImportJob job = jobRepository.save(DiscogsImportJob.builder()
                .nombreArchivo("single-letter-code.xlsx")
                .nombreHoja("Links")
                .status(DiscogsImportJobStatus.COMPLETED)
                .build());

        DiscogsImportRow first = parsedRow(job, 1, 111L);
        first.setInternalCode("F");
        first.setManualPriceUyu(new BigDecimal("800.00"));
        first.setManualCondition("VG+");
        first.setSourceStatus("DISPONIBLE");
        first.setArtist("First Artist");
        first.setTitle("First Album");

        DiscogsImportRow second = parsedRow(job, 2, 222L);
        second.setInternalCode("F");
        second.setManualPriceUyu(new BigDecimal("1200.00"));
        second.setManualCondition("NM");
        second.setSourceStatus("VENDIDO");
        second.setArtist("Second Artist");
        second.setTitle("Second Album");
        rowRepository.saveAll(List.of(first, second));

        DiscogsImportJobDTO imported = service.importParsedRows(job.getIdDiscogsImportJob());

        assertThat(imported.getImported()).isEqualTo(2);
        assertThat(imported.getRowsImported()).isEqualTo(2);
        assertThat(imported.getCatalogProductsAffected()).isEqualTo(2);
        assertThat(discoRepository.count()).isEqualTo(2);
        assertThat(imported.getRows())
                .extracting(DiscogsImportRowDTO::getImportedCatalogProductId)
                .doesNotContainNull()
                .doesNotHaveDuplicates();
        assertThat(discoRepository.findAll()).allSatisfy(disco -> {
            assertThat(disco.getCantidadCopias()).isEqualTo(1);
            assertThat(disco.getCodigoInterno()).isEqualTo("F");
            assertThat(disco.getProcedencia()).isEqualTo("Discogs");
        }).anySatisfy(disco -> {
            assertThat(disco.getDiscogsUrl()).endsWith("/release/111");
            assertThat(disco.getPrecioVenta()).isEqualByComparingTo("800.00");
            assertThat(disco.getCondicionFisica()).isEqualTo("VG+");
        }).anySatisfy(disco -> {
            assertThat(disco.getDiscogsUrl()).endsWith("/release/222");
            assertThat(disco.getPrecioVenta()).isEqualByComparingTo("1200.00");
            assertThat(disco.getCondicionFisica()).isEqualTo("NM");
        });

        service.importParsedRows(job.getIdDiscogsImportJob());
        assertThat(discoRepository.count()).isEqualTo(2);
        assertThat(discoRepository.findAll()).allSatisfy(disco ->
                assertThat(disco.getCantidadCopias()).isEqualTo(1));
    }

    @Test
    void singleNumberBusinessCodeDoesNotMergeDifferentDiscogsReleases() {
        DiscogsImportJob job = completedJob("single-number-code.xlsx");
        DiscogsImportRow first = parsedRow(job, 1, 111L);
        DiscogsImportRow second = parsedRow(job, 2, 222L);
        first.setInternalCode("1");
        second.setInternalCode("1");
        rowRepository.saveAll(List.of(first, second));

        DiscogsImportJobDTO imported = service.importParsedRows(job.getIdDiscogsImportJob());

        assertThat(imported.getImported()).isEqualTo(2);
        assertThat(imported.getCatalogProductsAffected()).isEqualTo(2);
        assertThat(discoRepository.findAll()).hasSize(2).allSatisfy(disco -> {
            assertThat(disco.getCodigoInterno()).isEqualTo("1");
            assertThat(disco.getCantidadCopias()).isEqualTo(1);
        });
    }

    @Test
    void repeatedSupplierOriginCodeDoesNotMergeDifferentDiscogsRows() {
        DiscogsImportJob job = completedJob("supplier-code.xlsx");

        DiscogsImportRow first = parsedRow(job, 2, 111L);
        first.setInternalCode("FP");
        first.setArtist("First Artist");
        first.setTitle("First Album");
        first.setNormalizedDiscogsUrl("https://discogs.com/release/111");

        DiscogsImportRow second = parsedRow(job, 3, 222L);
        second.setInternalCode("FP");
        second.setArtist("Second Artist");
        second.setTitle("Second Album");
        second.setNormalizedDiscogsUrl("https://discogs.com/release/222");

        rowRepository.saveAll(List.of(first, second));

        DiscogsImportJobDTO imported = service.importParsedRows(job.getIdDiscogsImportJob());

        assertThat(imported.getImported()).isEqualTo(2);
        assertThat(discoRepository.count()).isEqualTo(2);
        assertThat(discoRepository.findAll())
                .extracting(disco -> disco.getCodigoInterno())
                .containsOnly("FP");
    }

    @Test
    void sameDiscogsReleaseIncrementsStockEvenWhenBusinessCodeIsSingleLetter() {
        DiscogsImportJob job = completedJob("same-release.xlsx");
        DiscogsImportRow first = parsedRow(job, 1, 111L);
        DiscogsImportRow second = parsedRow(job, 2, 111L);
        first.setInternalCode("F");
        second.setInternalCode("F");
        rowRepository.saveAll(List.of(first, second));

        DiscogsImportJobDTO imported = service.importParsedRows(job.getIdDiscogsImportJob());

        assertThat(imported.getImported()).isEqualTo(2);
        assertThat(imported.getRowsImported()).isEqualTo(2);
        assertThat(imported.getCatalogProductsAffected()).isEqualTo(1);
        assertThat(imported.getNewProducts()).isEqualTo(1);
        assertThat(imported.getExistingProducts()).isZero();
        assertThat(imported.getRows())
                .extracting(DiscogsImportRowDTO::getImportedCatalogProductId)
                .containsOnly(imported.getRows().get(0).getImportedCatalogProductId());
        assertThat(discoRepository.findAll()).singleElement().satisfies(disco -> {
            assertThat(disco.getCodigoInterno()).isEqualTo("F");
            assertThat(disco.getCantidadCopias()).isEqualTo(2);
            assertThat(disco.getDiscogsUrl()).endsWith("/release/111");
        });
    }

    @Test
    void existingProductReceivesAdditionalBulkCopyAndReportsReuse() {
        DiscogsImportJob job = completedJob("existing-product.xlsx");
        DiscogsImportRow row = parsedRow(job, 2, 111L);
        rowRepository.save(row);

        discoRepository.save(Disco.builder()
                .codigoInterno("existing")
                .codigoQr(UUID.randomUUID().toString())
                .artista("Existing Artist")
                .album("Existing Album")
                .discogsReleaseId(111L)
                .discogsUrl("https://www.discogs.com/release/111")
                .cantidadCopias(1)
                .build());

        DiscogsImportJobDTO imported = service.importParsedRows(job.getIdDiscogsImportJob());

        assertThat(imported.getImported()).isEqualTo(1);
        assertThat(imported.getNewProducts()).isZero();
        assertThat(imported.getExistingProducts()).isEqualTo(1);
        assertThat(imported.getPhysicalCopiesImported()).isEqualTo(1);
        assertThat(discoRepository.findAll()).singleElement().satisfies(disco -> {
            assertThat(disco.getCantidadCopias()).isEqualTo(2);
            assertThat(qrCopyRepository.findByIdDiscoOrderByCopyNumber(disco.getIdDisco())).hasSize(2);
        });
    }

    @Test
    void sameWorkbookAsNewJobReceivesAgainInsteadOfReusingHistoricalRowReceipt() {
        DiscogsImportJob historical = completedJob("pin.xlsx");
        historical.setSourceFingerprint("same-workbook");
        jobRepository.save(historical);
        rowRepository.save(parsedRow(historical, 12, 111L));
        service.importParsedRows(historical.getIdDiscogsImportJob());

        DiscogsImportJob reimport = completedJob("pin.xlsx");
        reimport.setSourceFingerprint("same-workbook");
        jobRepository.save(reimport);
        rowRepository.save(parsedRow(reimport, 12, 111L));

        DiscogsImportJobDTO imported = service.importParsedRows(reimport.getIdDiscogsImportJob());

        assertThat(imported.getImported()).isEqualTo(1);
        assertThat(imported.getAlreadyImported()).isZero();
        assertThat(imported.getNewProducts()).isZero();
        assertThat(imported.getExistingProducts()).isEqualTo(1);
        assertThat(imported.getRows()).singleElement().satisfies(row -> {
            assertThat(row.getStatus()).isEqualTo("imported");
            assertThat(row.getCatalogProductResult()).isEqualTo("EXISTING_PRODUCT");
        });
        assertThat(discoRepository.findAll()).singleElement().satisfies(disco -> {
            assertThat(disco.getCantidadCopias()).isEqualTo(2);
            assertThat(qrCopyRepository.findByIdDiscoOrderByCopyNumber(disco.getIdDisco())).hasSize(2);
        });
    }

    @Test
    void retryingTheSamePersistedRowDoesNotReceiveAnotherCopy() {
        DiscogsImportJob job = completedJob("same-row-retry.xlsx");
        rowRepository.save(parsedRow(job, 2, 111L));

        DiscogsImportJobDTO first = service.importParsedRows(job.getIdDiscogsImportJob());
        DiscogsImportJobDTO retry = service.importParsedRows(job.getIdDiscogsImportJob());

        assertThat(first.getPhysicalCopiesImported()).isEqualTo(1);
        assertThat(retry.getPhysicalCopiesImported()).isEqualTo(1);
        assertThat(discoRepository.findAll()).singleElement().satisfies(disco -> {
            assertThat(disco.getCantidadCopias()).isEqualTo(1);
            assertThat(qrCopyRepository.findByIdDiscoOrderByCopyNumber(disco.getIdDisco())).hasSize(1);
        });
    }

    @Test
    void interruptedJobCanResumeWithoutRecreatingMetadataRow() {
        when(apiClient.newSession()).thenReturn(new DiscogsApiClient.ImportSession());
        when(apiClient.fetch(any(DiscogsApiClient.ImportSession.class), anyString(), anyLong()))
                .thenReturn(successResult(901L));

        DiscogsImportJob job = jobRepository.save(DiscogsImportJob.builder()
                .nombreArchivo("interrupted.xlsx")
                .nombreHoja("Links")
                .status(DiscogsImportJobStatus.PROCESSING)
                .build());
        DiscogsImportRow row = parsedRow(job, 2, 901L);
        row.setStatus(DiscogsImportRowStatus.FETCHING_DISCOGS);
        row.setMetadataStatus(DiscogsMetadataStatus.PROCESSING);
        rowRepository.saveAndFlush(row);

        service.resumeJob(job.getIdDiscogsImportJob());

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() ->
                assertThat(service.getJob(job.getIdDiscogsImportJob()).getMetadataFetched()).isEqualTo(1));
        assertThat(rowRepository.findById(row.getIdDiscogsImportRow()).orElseThrow().getStatus())
                .isEqualTo(DiscogsImportRowStatus.PARSED);
    }

    @Test
    void concurrentCatalogRetriesReceiveTheSamePersistedRowOnlyOnce() throws Exception {
        DiscogsImportJob job = completedJob("concurrent-retry.xlsx");
        rowRepository.save(parsedRow(job, 2, 902L));

        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            var first = callers.submit(() -> service.importParsedRows(job.getIdDiscogsImportJob()));
            var second = callers.submit(() -> service.importParsedRows(job.getIdDiscogsImportJob()));
            first.get();
            second.get();
        } finally {
            callers.shutdownNow();
        }

        assertThat(discoRepository.findAll()).singleElement().satisfies(disco -> {
            assertThat(disco.getCantidadCopias()).isEqualTo(1);
            assertThat(qrCopyRepository.findByIdDiscoOrderByCopyNumber(disco.getIdDisco())).hasSize(1);
        });
        assertThat(rowRepository.findByJobIdDiscogsImportJobOrderBySourceExcelRowNumber(
                job.getIdDiscogsImportJob())).singleElement()
                .extracting(DiscogsImportRow::getStatus)
                .isEqualTo(DiscogsImportRowStatus.IMPORTED);
    }

    @Test
    void importsFiveHundredDifferentReleasesSharingSingleLetterCodeWithoutLimitOrMerging() {
        DiscogsImportJob job = completedJob("five-hundred-releases.xlsx");
        List<DiscogsImportRow> rows = new ArrayList<>();
        for (int index = 0; index < 500; index++) {
            DiscogsImportRow row = parsedRow(job, index + 2, 100_000L + index);
            row.setInternalCode("F");
            row.setArtist("Artist " + index);
            row.setTitle("Album " + index);
            rows.add(row);
        }
        rowRepository.saveAll(rows);

        DiscogsImportJobDTO imported = service.importParsedRows(job.getIdDiscogsImportJob());

        assertThat(imported.getImported()).isEqualTo(500);
        assertThat(imported.getRowsImported()).isEqualTo(500);
        assertThat(imported.getCatalogProductsAffected()).isEqualTo(500);
        assertThat(discoRepository.findAll()).hasSize(500).allSatisfy(disco -> {
            assertThat(disco.getCodigoInterno()).isEqualTo("F");
            assertThat(disco.getCantidadCopias()).isEqualTo(1);
        });
        assertThat(discoRepository.findAll())
                .extracting(disco -> disco.getDiscogsUrl())
                .doesNotHaveDuplicates();
    }

    @Test
    void masterAndReleaseRowsResolvedToTheSameReleaseShareCatalogStock() {
        DiscogsImportJob job = jobRepository.save(DiscogsImportJob.builder()
                .nombreArchivo("master-release.xlsx")
                .nombreHoja("Links")
                .status(DiscogsImportJobStatus.COMPLETED)
                .build());

        DiscogsImportRow master = parsedRow(job, 2, 500L);
        master.setDiscogsType("master");
        master.setDiscogsId(500L);
        master.setMasterId(500L);
        master.setResolvedReleaseId(600L);
        master.setNormalizedDiscogsUrl("https://www.discogs.com/master/500");
        master.setInternalCode("FP");
        master.setArtist("Master title variant");

        DiscogsImportRow release = parsedRow(job, 3, 600L);
        release.setInternalCode("FP");
        release.setArtist("Release title variant");
        rowRepository.saveAll(List.of(master, release));

        DiscogsImportJobDTO imported = service.importParsedRows(job.getIdDiscogsImportJob());

        assertThat(imported.getImported()).isEqualTo(2);
        assertThat(discoRepository.findAll()).singleElement()
                .satisfies(disco -> assertThat(disco.getCantidadCopias()).isEqualTo(2));
        assertThat(imported.getRows())
                .extracting(DiscogsImportRowDTO::getImportedCatalogProductId)
                .containsOnly(imported.getRows().get(0).getImportedCatalogProductId());
    }

    @Test
    void importsTheRealFedePintosWorkbookIntoCatalogStockWithoutPartialRows() throws Exception {
        when(apiClient.newSession()).thenReturn(new DiscogsApiClient.ImportSession());
        when(apiClient.fetch(any(DiscogsApiClient.ImportSession.class), anyString(), anyLong()))
                .thenAnswer(invocation -> successResult(invocation.getArgument(2)));

        DiscogsImportJobDTO created;
        try (InputStream workbook = getClass().getResourceAsStream(
                "/discogs/DISCOS FEDE PINTOS.xlsx")) {
            assertThat(workbook).isNotNull();
            created = service.createJob(new MockMultipartFile(
                    "file",
                    "DISCOS FEDE PINTOS.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    workbook.readAllBytes()
            ));
        }

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            DiscogsImportJobDTO current = service.getJob(created.getId());
            assertThat(current.getStatus()).isEqualTo("completed_with_warnings");
            assertThat(current.getTotalRowsRead()).isEqualTo(44);
            assertThat(current.getMetadataFetched()).isEqualTo(44);
            assertThat(current.getReadyToImport()).isEqualTo(44);
            assertThat(current.getResolvedConcreteReleases()).isEqualTo(43);
            assertThat(current.getPhysicalCopiesToReceive()).isEqualTo(44);
        });

        DiscogsImportJobDTO imported = service.importParsedRows(created.getId());

        assertThat(imported.getImported()).isEqualTo(44);
        assertThat(imported.getPhysicalCopiesImported()).isEqualTo(44);
        assertThat(imported.getRows()).filteredOn(row -> "imported".equals(row.getStatus())).hasSize(44);
        assertThat(imported.getRows()).filteredOn(row -> "sold".equals(row.getStatus())).isEmpty();
        assertThat(imported.getRows()).filteredOn(row -> "ignored".equals(row.getStatus())).isEmpty();
        assertThat(imported.getRows()).filteredOn(row -> row.getImportedCatalogProductId() != null).hasSize(44);
        assertThat(discoRepository.findAll()).allSatisfy(disco -> {
            assertThat(disco.getCantidadCopias()).isPositive();
            assertThat(disco.getDiscogsUrl()).isNotBlank();
            assertThat(disco.getArtista()).isNotBlank();
            assertThat(disco.getAlbum()).isNotBlank();
            assertThat(disco.getCondicion().name()).isEqualTo("USADO");
        });

        int totalCopies = discoRepository.findAll().stream()
                .mapToInt(disco -> disco.getCantidadCopias() == null ? 0 : disco.getCantidadCopias())
                .sum();
        assertThat(totalCopies).isEqualTo(44);

        service.importParsedRows(created.getId());
        int copiesAfterRepeat = discoRepository.findAll().stream()
                .mapToInt(disco -> disco.getCantidadCopias() == null ? 0 : disco.getCantidadCopias())
                .sum();
        assertThat(copiesAfterRepeat).isEqualTo(totalCopies);
    }

    @Test
    void continuesImportingOtherRowsWhenOneRowFails() {
        DiscogsImportJob job = jobRepository.save(DiscogsImportJob.builder()
                .nombreArchivo("rollback.xlsx")
                .nombreHoja("Links")
                .status(DiscogsImportJobStatus.COMPLETED)
                .build());
        rowRepository.saveAll(List.of(
                parsedRow(job, 2, 3001L),
                parsedRow(job, 3, 3002L)
        ));

        int[] calls = {0};
        doAnswer(invocation -> {
            if (++calls[0] == 2) {
                throw new IllegalStateException("fallo de prueba al guardar audio");
            }
            return null;
        }).when(audioPreviewService).guardarDesdeTracks(anyLong(), any());

        DiscogsImportJobDTO imported = service.importParsedRows(job.getIdDiscogsImportJob());

        assertThat(imported.getImported()).isEqualTo(2);
        assertThat(imported.getFailed()).isZero();
        assertThat(discoRepository.count()).isEqualTo(2);
        assertThat(discoRepository.findAll()).allSatisfy(disco ->
                assertThat(disco.getCantidadCopias()).isEqualTo(1));
        assertThat(rowRepository.findByJobIdDiscogsImportJobOrderBySourceExcelRowNumber(
                job.getIdDiscogsImportJob()))
                .satisfiesExactly(
                        first -> assertThat(first.getStatus()).isEqualTo(DiscogsImportRowStatus.IMPORTED),
                        second -> {
                            assertThat(second.getStatus()).isEqualTo(DiscogsImportRowStatus.IMPORTED);
                            assertThat(second.getWarningMessage())
                                    .contains("YOUTUBE_UNAVAILABLE")
                                    .contains("fallo de prueba");
                        });
    }

    @Test
    void preparesZipFromPlainMappedDataAfterCatalogRelationIsDetached() throws Exception {
        DiscogsImportJob job = jobRepository.save(DiscogsImportJob.builder()
                .nombreArchivo("zip-lazy.xlsx")
                .nombreHoja("Links")
                .sourceFingerprint("zip-lazy-fingerprint")
                .status(DiscogsImportJobStatus.COMPLETED)
                .build());
        DiscogsImportRow row = rowRepository.save(parsedRow(job, 2, 999L));

        DiscogsImportJobDTO imported = service.importParsedRows(job.getIdDiscogsImportJob());
        assertThat(imported.getImported()).isEqualTo(1);

        Files.createDirectories(COVERS_DIRECTORY);
        Path cover = COVERS_DIRECTORY.resolve("999.jpg");
        Files.write(cover, new byte[]{1, 2, 3});
        DiscogsImportRow persisted = rowRepository.findById(row.getIdDiscogsImportRow()).orElseThrow();
        persisted.setCoverLocalPath(cover.toString());
        persisted.setImageUrl("/api/importaciones/discogs/covers/999.jpg");
        persisted.setCoverStatus(DiscogsCoverStatus.SUCCESS);
        persisted.setCoverErrorCode(null);
        rowRepository.saveAndFlush(persisted);

        DiscogsZipStatusDTO preparing = service.prepareCoversZip(job.getIdDiscogsImportJob());
        assertThat(preparing.getZipStatus()).isEqualTo("preparing");

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            DiscogsZipStatusDTO zipStatus = service.getCoversZipStatus(job.getIdDiscogsImportJob());
            assertThat(zipStatus.isZipReady()).isTrue();
            assertThat(zipStatus.getZipTotalCovers()).isEqualTo(1);
            assertThat(zipStatus.getZipProcessedCovers()).isEqualTo(1);
            assertThat(zipStatus.getZipAddedCovers()).isEqualTo(1);
            assertThat(zipStatus.getZipProgressPercentage()).isEqualTo(100);
        });

        Path zip = service.getPreparedCoversZip(job.getIdDiscogsImportJob());
        try (ZipFile archive = new ZipFile(zip.toFile())) {
            assertThat(archive.getEntry("discogs-summary.csv")).isNotNull();
            String summary = new String(archive.getInputStream(
                    archive.getEntry("discogs-summary.csv")).readAllBytes());
            assertThat(summary).contains("imported_catalog_id,qr_id");
            assertThat(summary).contains(imported.getRows().get(0).getImportedCatalogProductId().toString());
        }
    }

    @Test
    void preparesDownloadableZipWithWarningsWhenOnlySomeCoversAreAvailable() throws Exception {
        DiscogsImportJob job = jobRepository.save(DiscogsImportJob.builder()
                .nombreArchivo("zip-with-missing-covers.xlsx")
                .nombreHoja("Links")
                .sourceFingerprint("zip-with-missing-covers-fingerprint")
                .status(DiscogsImportJobStatus.COMPLETED)
                .build());
        Files.createDirectories(COVERS_DIRECTORY);
        Path cover = COVERS_DIRECTORY.resolve("2001.jpg");
        Files.write(cover, new byte[]{2, 0, 0, 1});

        DiscogsImportRow valid = parsedRow(job, 2, 2001L);
        valid.setCoverLocalPath(cover.toString());
        valid.setImageUrl("/api/importaciones/discogs/covers/2001.jpg");
        valid.setCoverStatus(DiscogsCoverStatus.SUCCESS);
        DiscogsImportRow unavailable = parsedRow(job, 3, 2002L);
        unavailable.setCoverStatus(DiscogsCoverStatus.UNAVAILABLE);
        unavailable.setCoverErrorCode("COVER_UNAVAILABLE");
        unavailable.setWarningMessage("COVER_UNAVAILABLE — Portada no informada por Discogs.");
        rowRepository.saveAllAndFlush(List.of(valid, unavailable));

        service.prepareCoversZip(job.getIdDiscogsImportJob());

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            DiscogsZipStatusDTO zipStatus = service.getCoversZipStatus(job.getIdDiscogsImportJob());
            assertThat(zipStatus.getZipStatus()).isEqualTo("ready_with_warnings");
            assertThat(zipStatus.isZipReady()).isTrue();
            assertThat(zipStatus.getZipTotalCovers()).isEqualTo(2);
            assertThat(zipStatus.getZipProcessedCovers()).isEqualTo(2);
            assertThat(zipStatus.getZipAddedCovers()).isEqualTo(1);
            assertThat(zipStatus.getZipFailedCovers()).isEqualTo(1);
            assertThat(zipStatus.getZipError()).isNull();
        });

        Path zip = service.getPreparedCoversZip(job.getIdDiscogsImportJob());
        try (ZipFile archive = new ZipFile(zip.toFile())) {
            assertThat(archive.stream().filter(entry -> entry.getName().endsWith(".jpg"))).hasSize(1);
            assertThat(archive.getEntry("errors.csv")).isNotNull();
            String errors = new String(archive.getInputStream(archive.getEntry("errors.csv")).readAllBytes());
            assertThat(errors).contains("\"3\"").contains("MISSING_LOCAL_FILE", "COVER_UNAVAILABLE");
        }
        assertThat(rowRepository.findByJobIdDiscogsImportJobOrderBySourceExcelRowNumber(
                job.getIdDiscogsImportJob()).get(1))
                .satisfies(row -> {
                    assertThat(row.getCoverStatus()).isEqualTo(DiscogsCoverStatus.UNAVAILABLE);
                    assertThat(row.getCoverErrorCode()).isEqualTo("COVER_UNAVAILABLE");
                    assertThat(row.getWarningMessage()).contains("portada local no estaba disponible");
                });
    }

    private MockMultipartFile fixture() throws Exception {
        return workbookWithUrls(List.of("https://discogs.com/release/999"));
    }

    private MockMultipartFile workbookWithUrls(List<String> urls) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Links");
            sheet.createRow(0).createCell(0).setCellValue("Discogs URL");
            for (int index = 0; index < urls.size(); index++) {
                sheet.createRow(index + 1).createCell(0).setCellValue(urls.get(index));
            }
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "links.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }

    private DiscogsApiClient.FetchResult successResult() {
        return successResult(999L);
    }

    private DiscogsApiClient.FetchResult successResult(long releaseId) {
        return new DiscogsApiClient.FetchResult(
                true,
                false,
                false,
                0,
                null,
                null,
                releaseId,
                "Artist " + releaseId,
                "Album " + releaseId,
                2001,
                "Electronic",
                "Label",
                "CAT-1",
                "Uruguay",
                "Techno",
                "VINILO",
                null,
                "CAT-1",
                "A1. Track",
                List.of(new TrackInfo("A1", "Track", null, "https://youtube.test/track"))
        );
    }

    private DiscogsImportJob completedJob(String filename) {
        return jobRepository.save(DiscogsImportJob.builder()
                .nombreArchivo(filename)
                .nombreHoja("Links")
                .status(DiscogsImportJobStatus.COMPLETED)
                .build());
    }

    private DiscogsImportRow parsedRow(DiscogsImportJob job, int rowNumber, long releaseId) {
        return DiscogsImportRow.builder()
                .job(job)
                .sourceExcelRowNumber(rowNumber)
                .discogsType("release")
                .discogsId(releaseId)
                .resolvedReleaseId(releaseId)
                .normalizedDiscogsUrl("https://discogs.com/release/" + releaseId)
                .artist("Artist")
                .title("Album")
                .format("VINILO")
                .catalogNumber("CAT-1")
                .internalCode("CAT-1")
                .sourceStatus("DISPONIBLE")
                .status(DiscogsImportRowStatus.PARSED)
                .metadataStatus(DiscogsMetadataStatus.SUCCESS)
                .coverStatus(DiscogsCoverStatus.UNAVAILABLE)
                .youtubeStatus(DiscogsYoutubeStatus.SUCCESS)
                .youtubeTracksFound(1)
                .youtubeTracksMissing(0)
                .catalogImportStatus(DiscogsCatalogImportStatus.READY)
                .tracksJson("[{\"label\":\"A1\",\"name\":\"Track\",\"mp3Url\":null,\"youtubeUrl\":\"https://youtube.test/track\"}]")
                .build();
    }
}
