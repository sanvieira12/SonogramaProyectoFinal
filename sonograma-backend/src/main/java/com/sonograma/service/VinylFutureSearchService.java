package com.sonograma.service;

import com.sonograma.dto.InvoiceItem;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Searches vinylfuture.com for a given InvoiceItem and returns the URL of the first matching product.
 *
 * vinylfuture.com uses path-based search: https://www.vinylfuture.com/{query+with+plus+spaces}
 * Product URLs match the pattern: https://www.vinylfuture.com/Artist_Album_Catalog_Vinyl__<numericId>
 *
 * Strategy:
 *  1. Search by catalog code alone (most precise).
 *  2. If no product link found, search by "artista album".
 */
@Slf4j
@Service
public class VinylFutureSearchService {

    private static final String BASE_URL = "https://www.vinylfuture.com/";
    private static final String USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    // Product URL pattern: ends with _Vinyl__<numericId>
    private static final Pattern PRODUCT_URL = Pattern.compile(
        "https://www\\.vinylfuture\\.com/.+_Vinyl__\\d+$"
    );
    private final Map<String, Optional<String>> searchCache = new ConcurrentHashMap<>();

    @Value("${sonograma.vinylfuture.delay-ms:400}")
    private long delayMs;

    @Value("${sonograma.vinylfuture.timeout-ms:10000}")
    private int timeoutMs;

    public Optional<String> buscar(InvoiceItem item) {
        String cacheKey = (item.codigoCatalogo() + "|" + item.artista() + "|" + item.album())
            .toLowerCase();
        return searchCache.computeIfAbsent(cacheKey, ignored -> buscarSinCache(item));
    }

    private Optional<String> buscarSinCache(InvoiceItem item) {
        // 1st attempt: catalog code (exact, fast)
        Optional<String> url = searchByQuery(item.codigoCatalogo());
        if (url.isPresent()) {
            log.debug("Found by catalog code '{}': {}", item.codigoCatalogo(), url.get());
            return url;
        }

        // 2nd attempt: artist + album
        String query = item.artista() + " " + item.album();
        url = searchByQuery(query);
        if (url.isPresent()) {
            log.debug("Found by artist+album '{}': {}", query, url.get());
        } else {
            log.debug("No result for '{}'", query);
        }
        return url;
    }

    private Optional<String> searchByQuery(String query) {
        throttle();
        String encodedQuery = encodePathSegment(query);
        String searchUrl = BASE_URL + encodedQuery;
        try {
            Document doc = Jsoup.connect(searchUrl)
                .userAgent(USER_AGENT)
                .timeout(timeoutMs)
                .followRedirects(true)
                .get();

            // Look for the first <a> whose href matches the product URL pattern
            for (Element a : doc.select("a[href]")) {
                String href = a.attr("abs:href");
                if (PRODUCT_URL.matcher(href).matches()) {
                    return Optional.of(href);
                }
            }
        } catch (IOException e) {
            log.warn("HTTP error searching vinylfuture for '{}': {}", query, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Encodes a search query for use as a URL path segment on vinylfuture.com.
     * Spaces become '+', special chars are percent-encoded.
     */
    private String encodePathSegment(String query) {
        return URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)
            .replace("%2B", "+")   // keep + as literal
            .replace("+", "+");    // spaces already become + via URLEncoder
    }

    private void throttle() {
        if (delayMs <= 0) return;
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
