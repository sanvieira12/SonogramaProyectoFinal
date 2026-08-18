package com.sonograma.service.importacion;

import com.sonograma.dto.DiscogsCoverZipRow;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

class DiscogsCoverServiceTest {

    @TempDir
    Path tempDir;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void downloadsCoverAndBuildsImageOnlyZip() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/cover.jpg", exchange -> {
            byte[] image = new byte[]{1, 2, 3, 4};
            exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, image.length);
            exchange.getResponseBody().write(image);
            exchange.close();
        });
        server.start();

        DiscogsCoverService service = new DiscogsCoverService(tempDir.toString());
        String remote = "http://localhost:" + server.getAddress().getPort() + "/cover.jpg";
        var cover = service.download(remote, 123);
        DiscogsCoverZipRow row = DiscogsCoverZipRow.builder()
                .sourceExcelRowNumber(2)
                .sourceDiscogsId(123L)
                .resolvedReleaseId(123L)
                .artist("Artist / Name")
                .title("Title: One")
                .imageUrl(cover.publicUrl())
                .coverLocalPath(cover.localPath().toString())
                .metadataStatus("SUCCESS")
                .coverStatus("SUCCESS")
                .build();

        Path zip = tempDir.resolve("complete.zip");
        var result = service.buildZip(zip, List.of(row, row), ignored -> {});

        assertThat(cover.available()).isTrue();
        assertThat(Files.readAllBytes(cover.localPath())).containsExactly(1, 2, 3, 4);
        try (ZipFile archive = new ZipFile(zip.toFile())) {
            var names = archive.stream().map(entry -> entry.getName()).toList();
            assertThat(names).containsExactly(
                    "discogs-summary.csv",
                    "2_123_Artist _ Name_Title_ One.jpg",
                    "2_123_Artist _ Name_Title_ One-2.jpg"
            );
            assertThat(names).noneMatch(name -> name.contains("/audio/") || name.endsWith(".mp3"));
            String summary = new String(archive.getInputStream(archive.getEntry("discogs-summary.csv")).readAllBytes());
            assertThat(summary).contains("excel_row,discogs_url,source_type,source_discogs_id");
            assertThat(summary).contains("\"123\",\"123\",\"Artist / Name\",\"Title: One\"");
            assertThat(result.added()).isEqualTo(2);
            assertThat(result.failed()).isZero();
        } finally {
            Files.deleteIfExists(zip);
        }
    }

    @Test
    void createsPartialZipWithUniqueNamesSummaryAndErrorsWithoutDownloadingAgain() throws Exception {
        DiscogsCoverService service = new DiscogsCoverService(tempDir.toString());
        Path stored = tempDir.resolve("500.jpg");
        Files.write(stored, new byte[]{9, 8, 7});

        DiscogsCoverZipRow first = zipRow(4, 500L, stored.toString());
        DiscogsCoverZipRow duplicateName = zipRow(5, 500L, stored.toString());
        DiscogsCoverZipRow noImage = zipRow(6, 600L, null);
        noImage.setImageUrl(null);
        noImage.setCoverStatus("UNAVAILABLE");
        noImage.setCoverErrorCode("COVER_UNAVAILABLE");
        DiscogsCoverZipRow missing = zipRow(7, 700L, tempDir.resolve("missing.jpg").toString());

        Path zip = tempDir.resolve("partial.zip");
        var result = service.buildZip(zip, List.of(first, duplicateName, noImage, missing), ignored -> {});

        assertThat(result.total()).isEqualTo(4);
        assertThat(result.added()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(2);
        assertThat(result.missingLocalRows()).containsExactly(6, 7);
        try (ZipFile archive = new ZipFile(zip.toFile())) {
            assertThat(archive.getEntry("discogs-summary.csv")).isNotNull();
            assertThat(archive.getEntry("errors.csv")).isNotNull();
            assertThat(archive.stream().filter(entry -> entry.getName().endsWith(".jpg"))).hasSize(2);
            assertThat(archive.stream().map(entry -> entry.getName()).toList()).doesNotHaveDuplicates();
            String errors = new String(archive.getInputStream(archive.getEntry("errors.csv")).readAllBytes());
            assertThat(errors)
                    .contains("\"6\"")
                    .contains("\"7\"")
                    .contains("MISSING_LOCAL_FILE")
                    .contains("COVER_UNAVAILABLE");
        }
    }

    @Test
    void nullImageUrlIsReportedWithoutAbortingValidCovers() throws Exception {
        DiscogsCoverService service = new DiscogsCoverService(tempDir.toString());
        Path stored = tempDir.resolve("801.jpg");
        Files.write(stored, new byte[]{8, 0, 1});
        DiscogsCoverZipRow valid = zipRow(8, 801L, stored.toString());
        DiscogsCoverZipRow missing = zipRow(9, 802L, null);
        missing.setImageUrl(null);

        Path zip = tempDir.resolve("null-image-url.zip");
        var result = service.buildZip(zip, List.of(valid, missing), ignored -> {});

        assertThat(result.added()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.missingLocalRows()).containsExactly(9);
        try (ZipFile archive = new ZipFile(zip.toFile())) {
            assertThat(archive.stream().filter(entry -> entry.getName().endsWith(".jpg"))).hasSize(1);
            assertThat(archive.getEntry("errors.csv")).isNotNull();
            String errors = new String(archive.getInputStream(archive.getEntry("errors.csv")).readAllBytes());
            assertThat(errors).contains("\"9\"").contains("MISSING_LOCAL_FILE");
        }
    }

    @Test
    void blankImageUrlsAreReportedWithoutThrowing() throws Exception {
        DiscogsCoverService service = new DiscogsCoverService(tempDir.toString());

        for (int index = 0; index < 2; index++) {
            DiscogsCoverZipRow row = zipRow(10 + index, 900L + index, null);
            row.setImageUrl(index == 0 ? "" : "   ");
            Path zip = tempDir.resolve("blank-image-url-" + index + ".zip");

            var result = service.buildZip(zip, List.of(row), ignored -> {});

            assertThat(result.added()).isZero();
            assertThat(result.failed()).isEqualTo(1);
            try (ZipFile archive = new ZipFile(zip.toFile())) {
                assertThat(archive.getEntry("errors.csv")).isNotNull();
            }
        }
    }

    @Test
    void nonexistentLocalFileDoesNotInterruptRemainingRows() throws Exception {
        DiscogsCoverService service = new DiscogsCoverService(tempDir.toString());
        DiscogsCoverZipRow missing = zipRow(12, 1001L, tempDir.resolve("gone.jpg").toString());
        Path stored = tempDir.resolve("1002.jpg");
        Files.write(stored, new byte[]{1, 0, 0, 2});
        DiscogsCoverZipRow valid = zipRow(13, 1002L, stored.toString());

        Path zip = tempDir.resolve("missing-local-file.zip");
        var result = service.buildZip(zip, List.of(missing, valid), ignored -> {});

        assertThat(result.added()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.missingLocalRows()).containsExactly(12);
        try (ZipFile archive = new ZipFile(zip.toFile())) {
            assertThat(archive.stream().filter(entry -> entry.getName().endsWith(".jpg"))).hasSize(1);
            assertThat(archive.getEntry("errors.csv")).isNotNull();
        }
    }

    @Test
    void malformedLocalPathAndUnexpectedImageUrlAreHandledAsMissing() throws Exception {
        DiscogsCoverService service = new DiscogsCoverService(tempDir.toString());
        DiscogsCoverZipRow row = zipRow(14, 1100L, "bad\u0000path");
        row.setImageUrl("https://images.discogs.com/1100.jpg");

        Path zip = tempDir.resolve("malformed-local-path.zip");
        var result = service.buildZip(zip, List.of(row), ignored -> {});

        assertThat(result.added()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.missingLocalRows()).containsExactly(14);
        try (ZipFile archive = new ZipFile(zip.toFile())) {
            assertThat(archive.getEntry("errors.csv")).isNotNull();
        }
    }

    private DiscogsCoverZipRow zipRow(int rowNumber, long releaseId, String localPath) {
        return DiscogsCoverZipRow.builder()
                .sourceExcelRowNumber(rowNumber)
                .discogsUrl("https://www.discogs.com/release/" + releaseId)
                .sourceType("RELEASE")
                .sourceDiscogsId(releaseId)
                .resolvedReleaseId(releaseId)
                .artist("Same Artist")
                .title("Same Title")
                .metadataStatus("SUCCESS")
                .coverStatus("SUCCESS")
                .youtubeStatus("SUCCESS")
                .catalogImportStatus("READY")
                .coverLocalPath(localPath)
                .build();
    }
}
