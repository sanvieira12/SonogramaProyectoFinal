package com.sonograma.service.importacion;

import com.sonograma.entity.DiscogsImportRow;
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
        DiscogsImportRow row = DiscogsImportRow.builder()
                .discogsId(123L)
                .resolvedReleaseId(123L)
                .artist("Artist / Name")
                .title("Title: One")
                .imageUrl(cover.publicUrl())
                .build();

        Path zip = service.buildZip(List.of(row, row));

        assertThat(cover.available()).isTrue();
        assertThat(Files.readAllBytes(cover.localPath())).containsExactly(1, 2, 3, 4);
        try (ZipFile archive = new ZipFile(zip.toFile())) {
            var names = archive.stream().map(entry -> entry.getName()).toList();
            assertThat(names).containsExactly("discogs-summary.csv", "123 - Artist _ Name - Title_ One.jpg");
            assertThat(names).noneMatch(name -> name.contains("/audio/") || name.endsWith(".mp3"));
            String summary = new String(archive.getInputStream(archive.getEntry("discogs-summary.csv")).readAllBytes());
            assertThat(summary).contains("row_number,discogs_id,artist,title");
            assertThat(summary).contains("\"123\",\"Artist / Name\",\"Title: One\"");
        } finally {
            Files.deleteIfExists(zip);
        }
    }
}
