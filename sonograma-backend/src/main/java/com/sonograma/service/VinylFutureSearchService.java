package com.sonograma.service;

import com.sonograma.dto.InvoiceItem;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
 *  1. Direct/catalog-code path.
 *  2. Existing search by exact catalog code.
 *  3. Search by artist + album + catalog code.
 *  4. Existing fallback search by artist + album.
 */
@Slf4j
@Service
public class VinylFutureSearchService {

    private static final String BASE_URL = "https://www.vinylfuture.com/";
    private static final String USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    // Product URL pattern: VinylFuture sometimes omits "_Vinyl" before the id.
    private static final Pattern PRODUCT_URL = Pattern.compile(
        "https://www\\.vinylfuture\\.com/.+__\\d+$"
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
        String code = item.codigoCatalogo();

        Optional<String> url = searchByQuery(code, code, true);
        if (url.isPresent()) {
            log.debug("Found by direct catalog path '{}': {}", code, url.get());
            return url;
        }

        url = searchByQuery(code, code, false);
        if (url.isPresent()) {
            log.debug("Found by exact catalog code '{}': {}", code, url.get());
            return url;
        }

        String queryWithCode = item.artista() + " " + item.album() + " " + code;
        url = searchByQuery(queryWithCode, code, false);
        if (url.isPresent()) {
            log.debug("Found by artist+album+code '{}': {}", queryWithCode, url.get());
            return url;
        }

        String query = item.artista() + " " + item.album();
        url = searchByQuery(query, code, false);
        if (url.isPresent()) {
            log.debug("Found by artist+album '{}': {}", query, url.get());
        } else {
            log.debug("No result for code='{}', artist='{}', album='{}'",
                code, item.artista(), item.album());
        }
        return url;
    }

    private Optional<String> searchByQuery(String query, String catalogCode, boolean directCodePath) {
        if (query == null || query.isBlank()) return Optional.empty();
        throttle();
        String encodedQuery = encodePathSegment(query);
        String searchUrl = BASE_URL + encodedQuery;
        try {
            Document doc = Jsoup.connect(searchUrl)
                .userAgent(USER_AGENT)
                .timeout(timeoutMs)
                .followRedirects(true)
                .get();

            List<String> productLinks = new ArrayList<>();
            for (Element a : doc.select("a[href]")) {
                String href = a.attr("abs:href");
                if (PRODUCT_URL.matcher(href).matches()) {
                    productLinks.add(href);
                }
            }
            Optional<String> exactCodeMatch = productLinks.stream()
                .filter(href -> containsExactCode(href, catalogCode))
                .min(Comparator.comparingInt(String::length));
            if (exactCodeMatch.isPresent()) return exactCodeMatch;
            if (directCodePath && productLinks.isEmpty() && PRODUCT_URL.matcher(doc.location()).matches()) {
                return Optional.of(doc.location());
            }
            return productLinks.stream().findFirst();
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

    private boolean containsExactCode(String url, String catalogCode) {
        if (url == null || catalogCode == null || catalogCode.isBlank()) return false;
        String decoded = decode(url);
        String normalizedPath = decoded.toLowerCase(Locale.ROOT);
        String normalizedCode = catalogCode.toLowerCase(Locale.ROOT);
        return Pattern.compile("(^|[^\\p{L}\\p{N}])" + Pattern.quote(normalizedCode)
                + "([^\\p{L}\\p{N}]|$)")
            .matcher(normalizedPath)
            .find();
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return value;
        }
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
