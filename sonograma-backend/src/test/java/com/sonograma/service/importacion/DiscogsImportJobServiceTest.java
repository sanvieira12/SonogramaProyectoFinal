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
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
        return new DiscogsApiClient.FetchResult(
                true,
                false,
                false,
                0,
                null,
                null,
                999L,
                "Artist",
                "Album",
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
