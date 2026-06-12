package com.sonograma.service.importacion;

import com.sonograma.dto.DiscogsImportJobDTO;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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

    @MockBean
    private DiscogsApiClient apiClient;

    @BeforeEach
    void clean() {
        rowRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void rateLimitKeepsTheJobAndRowsAvailableForRetry() throws Exception {
        when(apiClient.fetch(anyString(), anyLong()))
                .thenReturn(DiscogsApiClient.FetchResult.failure(true, 1, "HTTP 429"));

        DiscogsImportJobDTO created = service.createJob(fixture());
        assertThat(created.getTotalRowsRead()).isEqualTo(1);
        assertThat(created.getValidReleaseUrls()).isEqualTo(1);

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            DiscogsImportJobDTO current = service.getJob(created.getId());
            assertThat(current.getStatus()).isEqualTo("completed_with_errors");
            assertThat(current.getRateLimited()).isEqualTo(1);
            assertThat(current.getRows()).singleElement().satisfies(row -> {
                assertThat(row.getStatus()).isEqualTo("rate_limited");
                assertThat(row.getRetryCount()).isEqualTo(3);
                assertThat(row.getDiscogsId()).isEqualTo(999L);
            });
        });
    }

    private MockMultipartFile fixture() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Links");
            sheet.createRow(0).createCell(0).setCellValue("Discogs URL");
            sheet.createRow(1).createCell(0).setCellValue("https://discogs.com/release/999");
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "links.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }
}
