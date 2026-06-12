package com.sonograma.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonograma.dto.VinylPageData;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VinylFutureScraperServiceTest {

    private final VinylFutureScraperService service = new VinylFutureScraperService(new ObjectMapper());

    @Test
    void extractsMetadataAndRelativeAudioFromSupplierHtml() {
        String html = """
            <html><head>
              <meta property="og:image" content="/covers/release.jpg">
              <script type="application/ld+json">
                {"@type":"Product","name":"Scraped title","description":"Limited edition",
                 "sku":"CAT-100","image":"/covers/json.jpg","offers":{"price":"12.50"}}
              </script>
            </head><body>
              <h1>Fallback title</h1>
              <dl>
                <dt>Artist</dt><dd>Test Artist</dd>
                <dt>Label</dt><dd>Test Records</dd>
                <dt>Genre</dt><dd>House</dd>
                <dt>Year</dt><dd>2024</dd>
                <dt>Country</dt><dd>Germany</dd>
                <dt>Format</dt><dd>2x12"</dd>
              </dl>
              <a data-position="A1" data-title="First Track" data-mp3="/audio/first.mp3"></a>
              <script>window.preview = "/audio/first.mp3";</script>
            </body></html>
            """;
        Document doc = Jsoup.parse(html, "https://supplier.example/products/100");

        VinylPageData result = service.extractPageData(doc, "https://supplier.example/products/100");

        assertEquals("Test Artist", result.artist());
        assertEquals("Scraped title", result.title());
        assertEquals("CAT-100", result.code());
        assertEquals("Test Records", result.label());
        assertEquals("House", result.genre());
        assertEquals(2024, result.year());
        assertEquals("Germany", result.country());
        assertEquals("2x12\"", result.format());
        assertEquals(new BigDecimal("12.50"), result.purchasePrice());
        assertEquals("https://supplier.example/covers/release.jpg", result.frontImageUrl());
        assertEquals(1, result.tracks().size());
        assertEquals("A1", result.tracks().get(0).label());
        assertEquals("First Track", result.tracks().get(0).name());
        assertEquals("https://supplier.example/audio/first.mp3", result.tracks().get(0).mp3Url());
    }

    @Test
    void buildsLegacyDeejayPreviewWhenVinylFutureUsesPlayTrackIds() {
        String html = """
            <a id="playTrack_106278_a"><b>A1</b><span class="trackname">Legacy Track</span></a>
            """;
        Document doc = Jsoup.parse(html, "https://www.vinylfuture.com/release_Vinyl__106278");

        VinylPageData result = service.extractPageData(
            doc, "https://www.vinylfuture.com/release_Vinyl__106278");

        assertEquals(1, result.tracks().size());
        assertEquals("A1", result.tracks().get(0).label());
        assertEquals("Legacy Track", result.tracks().get(0).name());
        assertEquals("https://www.deejay.de/streamit/7/8/106278a.mp3",
            result.tracks().get(0).mp3Url());
    }
}
