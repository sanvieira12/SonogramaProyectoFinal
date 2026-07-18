package com.sonograma.service.importacion;

import com.sonograma.dto.DiscogsImportJobDTO;
import com.sonograma.dto.DiscogsImportRowDTO;
import com.sonograma.dto.TrackInfo;
import com.sonograma.entity.DiscogsImportJob;
import com.sonograma.entity.DiscogsImportRow;
import com.sonograma.enums.DiscogsImportJobStatus;
import com.sonograma.enums.DiscogsImportRowStatus;
import com.sonograma.repository.DiscoRepository;
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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Autowired
    private DiscogsImportJobService service;

    @Autowired
    private DiscogsImportRowRepository rowRepository;

    @Autowired
    private DiscogsImportJobRepository jobRepository;

    @Autowired
    private DiscoRepository discoRepository;

    @MockBean
    private DiscogsApiClient apiClient;

    @MockBean
    private AudioPreviewService audioPreviewService;

    @BeforeEach
    void clean() {
        reset(apiClient);
        rowRepository.deleteAll();
        jobRepository.deleteAll();
        discoRepository.deleteAll();
    }

    @Test
    void rateLimitKeepsTheJobAndRowsAvailableForRetry() throws Exception {
        when(apiClient.newSession()).thenReturn(new DiscogsApiClient.ImportSession());
        when(apiClient.fetch(any(DiscogsApiClient.ImportSession.class), anyString(), anyLong()))
                .thenReturn(DiscogsApiClient.FetchResult.failure(true, 1, "HTTP 429"));

        DiscogsImportJobDTO created = service.createJob(fixture());
        assertThat(created.getTotalRowsRead()).isEqualTo(1);
        assertThat(created.getValidReleaseUrls()).isEqualTo(1);

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            DiscogsImportJobDTO current = service.getJob(created.getId());
            assertThat(current.getStatus()).isEqualTo("completed_with_errors");
            assertThat(current.getRateLimited()).isEqualTo(1);
            assertThat(current.getRows()).singleElement().satisfies(row -> {
                assertThat(row.getStatus()).isEqualTo("pending_retry");
                assertThat(row.getRetryCount()).isEqualTo(1);
                assertThat(row.getDiscogsId()).isEqualTo(999L);
                assertThat(row.getErrorMessage()).contains("pendiente");
            });
        });

        DiscogsImportJobDTO imported = service.importParsedRows(created.getId());
        assertThat(imported.getImported()).isZero();
        assertThat(imported.getRows()).singleElement()
                .satisfies(row -> assertThat(row.getImportedCatalogProductId()).isNull());
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
            assertThat(current.getStatus()).isEqualTo("completed");
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
    void importParsedRowsMergesDuplicateDiscogsRowsIntoExistingCatalogStock() throws Exception {
        DiscogsImportJob job = jobRepository.save(DiscogsImportJob.builder()
                .nombreArchivo("duplicate.xlsx")
                .nombreHoja("Links")
                .status(DiscogsImportJobStatus.COMPLETED)
                .build());
        rowRepository.saveAll(List.of(
                parsedRow(job, 1, 999L),
                parsedRow(job, 2, 1000L)
        ));

        DiscogsImportJobDTO imported = service.importParsedRows(job.getIdDiscogsImportJob());

        assertThat(imported.getImported()).isEqualTo(2);
        assertThat(discoRepository.count()).isEqualTo(1);
        assertThat(imported.getRows())
                .extracting(DiscogsImportRowDTO::getImportedCatalogProductId)
                .doesNotContainNull()
                .hasSize(2)
                .allMatch(id -> id.equals(imported.getRows().get(0).getImportedCatalogProductId()));
        assertThat(discoRepository.findAll()).singleElement().satisfies(disco -> {
            assertThat(disco.getCantidadCopias()).isEqualTo(2);
            assertThat(disco.getCodigoInterno()).isEqualTo("CAT-1");
            assertThat(disco.getProcedencia()).isEqualTo("Discogs");
        });

        service.importParsedRows(job.getIdDiscogsImportJob());
        assertThat(discoRepository.count()).isEqualTo(1);
        assertThat(discoRepository.findAll()).singleElement()
                .extracting(disco -> disco.getCantidadCopias())
                .isEqualTo(2);
    }

    @Test
    void repeatedSupplierOriginCodeDoesNotMergeDifferentDiscogsRows() {
        DiscogsImportJob job = jobRepository.save(DiscogsImportJob.builder()
                .nombreArchivo("supplier-code.xlsx")
                .nombreHoja("Links")
                .status(DiscogsImportJobStatus.COMPLETED)
                .build());

        DiscogsImportRow first = parsedRow(job, 2, 2001L);
        first.setInternalCode("FP");
        first.setArtist("First Artist");
        first.setTitle("First Album");
        first.setNormalizedDiscogsUrl("https://discogs.com/release/2001");

        DiscogsImportRow second = parsedRow(job, 3, 2002L);
        second.setInternalCode("FP");
        second.setArtist("Second Artist");
        second.setTitle("Second Album");
        second.setNormalizedDiscogsUrl("https://discogs.com/release/2002");

        rowRepository.saveAll(List.of(first, second));

        DiscogsImportJobDTO imported = service.importParsedRows(job.getIdDiscogsImportJob());

        assertThat(imported.getImported()).isEqualTo(2);
        assertThat(discoRepository.count()).isEqualTo(2);
        assertThat(discoRepository.findAll())
                .extracting(disco -> disco.getCodigoInterno())
                .containsOnly("FP");
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
            assertThat(current.getStatus()).isEqualTo("completed");
            assertThat(current.getTotalRowsRead()).isEqualTo(44);
            assertThat(current.getMetadataFetched()).isEqualTo(40);
            assertThat(current.getReadyToImport()).isEqualTo(40);
        });

        DiscogsImportJobDTO imported = service.importParsedRows(created.getId());

        assertThat(imported.getImported()).isEqualTo(40);
        assertThat(imported.getRows()).filteredOn(row -> "imported".equals(row.getStatus())).hasSize(40);
        assertThat(imported.getRows()).filteredOn(row -> "sold".equals(row.getStatus())).hasSize(3);
        assertThat(imported.getRows()).filteredOn(row -> "ignored".equals(row.getStatus())).hasSize(1);
        assertThat(imported.getRows()).filteredOn(row -> row.getImportedCatalogProductId() != null).hasSize(40);
        assertThat(discoRepository.findAll()).allSatisfy(disco -> {
            assertThat(disco.getCantidadCopias()).isPositive();
            assertThat(disco.getDiscogsUrl()).isNotBlank();
            assertThat(disco.getArtista()).isNotBlank();
            assertThat(disco.getAlbum()).isNotBlank();
        });

        int totalCopies = discoRepository.findAll().stream()
                .mapToInt(disco -> disco.getCantidadCopias() == null ? 0 : disco.getCantidadCopias())
                .sum();
        assertThat(totalCopies).isEqualTo(40);

        service.importParsedRows(created.getId());
        int copiesAfterRepeat = discoRepository.findAll().stream()
                .mapToInt(disco -> disco.getCantidadCopias() == null ? 0 : disco.getCantidadCopias())
                .sum();
        assertThat(copiesAfterRepeat).isEqualTo(totalCopies);
    }

    @Test
    void rollsBackAllCatalogRowsWhenOneRowFails() {
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

        assertThatThrownBy(() -> service.importParsedRows(job.getIdDiscogsImportJob()))
                .isInstanceOf(com.sonograma.exception.NegocioException.class)
                .hasMessageContaining("Fila Excel 3")
                .hasMessageContaining("LINK DE DISCOGS")
                .hasMessageContaining("fallo de prueba");

        assertThat(discoRepository.count()).isZero();
        assertThat(rowRepository.findByJobIdDiscogsImportJobOrderBySourceExcelRowNumber(
                job.getIdDiscogsImportJob()))
                .allSatisfy(row -> {
                    assertThat(row.getStatus()).isEqualTo(DiscogsImportRowStatus.PARSED);
                    assertThat(row.getImportedCatalogProduct()).isNull();
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
                .tracksJson("[{\"label\":\"A1\",\"name\":\"Track\",\"mp3Url\":null,\"youtubeUrl\":\"https://youtube.test/track\"}]")
                .build();
    }
}
