package com.sonograma.service;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.TrackInfo;
import com.sonograma.dto.VinylPageData;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VinylFutureAssetServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesCoverAndMp3AndReturnsLocalUrls() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/cover.jpg", exchange -> {
            byte[] body = "fake-jpeg".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/a1.mp3", exchange -> {
            byte[] body = "fake-mp3".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "audio/mpeg");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            VinylFutureAssetService service = new VinylFutureAssetService(tempDir.toString());
            InvoiceItem item = new InvoiceItem(
                "CAT-1", "Artist", "Album", "12", BigDecimal.ONE, 1, BigDecimal.ONE);
            VinylPageData page = new VinylPageData(
                baseUrl + "/release",
                "Artist", "Album", "CAT-1", "Label", "Genre", 2026,
                "Germany", "12", "New", "Desc", BigDecimal.ONE,
                baseUrl + "/cover.jpg", null,
                List.of(new TrackInfo("A1", "Track One", baseUrl + "/a1.mp3", null))
            );

            VinylPageData stored = service.storeAssets(item, page);

            assertThat(stored.frontImageUrl()).startsWith("/api/importar/vinylfuture/media/");
            assertThat(stored.tracks().get(0).mp3Url()).startsWith("/api/importar/vinylfuture/media/");
            assertThat(service.relativePath(stored.frontImageUrl()))
                .isEqualTo("CAT-1 - Artist - Album/cover.jpg");
            assertThat(service.relativePath(stored.tracks().get(0).mp3Url()))
                .isEqualTo("CAT-1 - Artist - Album/A1 - Track One.mp3");
            assertThat(Files.walk(tempDir).filter(Files::isRegularFile)).hasSize(2);
            assertThat(service.localPath(stored.frontImageUrl())).isRegularFile();
            assertThat(service.localPath(stored.tracks().get(0).mp3Url())).isRegularFile();
            assertThat(service.load(service.relativePath(stored.frontImageUrl())).exists()).isTrue();
        } finally {
            server.stop(0);
        }
    }
}
