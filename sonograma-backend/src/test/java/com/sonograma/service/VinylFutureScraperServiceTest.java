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

    @Test
    void ignoresBlankAudioElementsWithoutTrackPosition() {
        String html = """
            <audio src=""></audio>
            <a href="/preview.mp3"></a>
            """;
        Document doc = Jsoup.parse(html, "https://www.vinylfuture.com/release_Vinyl__106278");

        VinylPageData result = service.extractPageData(
            doc, "https://www.vinylfuture.com/release_Vinyl__106278");

        assertEquals(1, result.tracks().size());
        assertEquals("https://www.vinylfuture.com/preview.mp3", result.tracks().get(0).mp3Url());
    }

    @Test
    void prefersVisibleProductMetadataOverWebsiteJsonLd() {
        String html = """
            <script type="application/ld+json">{"@type":"WebSite","name":"vinylfuture.com"}</script>
            <article class="single_product">
              <div class="artikel">
                <div class="artist"><h1 itemprop="publisher">Betonkust</h1></div>
                <div class="title"><h1 itemprop="inalbum name">Tropicana Tracks Two</h1></div>
                <div class="labelContainer">
                  <h1 itemprop="alternateName">ALT025</h1>
                  <h3 itemprop="provider">Altered Circuits</h3>
                </div>
              </div>
              <div class="product_infos">
                <div class="infos">
                  <span class="format"><b>Format:</b> <span class="medium disc1">12inch Vinyl</span></span>
                </div>
              </div>
            </article>
            """;
        Document doc = Jsoup.parse(html,
            "https://www.vinylfuture.com/Betonkust_Tropicana_Tracks_Two_ALT025_Vinyl__1225612");

        VinylPageData result = service.extractPageData(doc,
            "https://www.vinylfuture.com/Betonkust_Tropicana_Tracks_Two_ALT025_Vinyl__1225612");

        assertEquals("Betonkust", result.artist());
        assertEquals("Tropicana Tracks Two", result.title());
        assertEquals("ALT025", result.code());
        assertEquals("Altered Circuits", result.label());
        assertEquals("12inch Vinyl", result.format());
    }
}
