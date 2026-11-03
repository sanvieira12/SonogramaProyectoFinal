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
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Finds the supplier product page without accepting reused catalogue codes blindly. */
@Slf4j
@Service
public class VinylFutureSearchService {

    private static final String USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final Pattern PRODUCT_URL = Pattern.compile(
        "https://www\\.(?:deejay\\.de|vinylfuture\\.com)/.+__\\d+$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PRODUCT_URL_IN_TEXT = Pattern.compile(
        "https://www\\.(?:deejay\\.de|vinylfuture\\.com)/[^\\s\\]\\)\\\"'<>]+__\\d+",
        Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> WEAK_TOKENS = Set.of(
        "a", "an", "and", "de", "del", "der", "die", "el", "en", "ep", "la", "las",
        "le", "les", "lp", "of", "the", "und", "vinyl", "vol"
    );

    private final Map<String, String> searchCache = new ConcurrentHashMap<>();

    @Value("${sonograma.vinylfuture.base-url:https://www.deejay.de/}")
    private String baseUrl;

    @Value("${sonograma.vinylfuture.archive-index-url:https://monthlyvinyl.net/r/}")
    private String archiveIndexUrl;

    @Value("${sonograma.vinylfuture.reader-url-prefix:}")
    private String readerUrlPrefix;

    @Value("${sonograma.vinylfuture.delay-ms:400}")
    private long delayMs;

    @Value("${sonograma.vinylfuture.timeout-ms:10000}")
    private int timeoutMs;

    public Optional<String> buscar(InvoiceItem item) {
        String cacheKey = normalize(item.codigoCatalogo() + "|" + item.artista() + "|" + item.album());
        String cached = searchCache.get(cacheKey);
        if (cached != null) return Optional.of(cached);

        Optional<String> result = buscarSinCache(item);
        // A temporary supplier/network failure must not poison the application cache.
        result.ifPresent(url -> searchCache.put(cacheKey, url));
        return result;
    }

    private Optional<String> buscarSinCache(InvoiceItem item) {
        List<String> queries = List.of(
            safe(item.codigoCatalogo()),
            join(item.artista(), item.album(), item.codigoCatalogo()),
            join(item.artista(), item.album())
        );
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (String query : queries) {
            candidates.addAll(searchByQuery(query));
            Optional<String> confident = bestCandidate(item, candidates);
            if (confident.isPresent()) {
                log.debug("VinylFuture match query='{}' url='{}'", query, confident.get());
                return confident;
            }
        }

        Optional<String> archived = searchArchiveIndex(item);
        if (archived.isPresent()) {
            log.info("VinylFuture archived supplier page recovered for '{}': {}",
                itemLabel(item), archived.get());
            return archived;
        }
        log.debug("No supplier result for code='{}', artist='{}', album='{}'",
            item.codigoCatalogo(), item.artista(), item.album());
        return Optional.empty();
    }

    private List<String> searchByQuery(String query) {
        if (query == null || query.isBlank()) return List.of();
        throttle();
        String searchUrl = trailingSlash(baseUrl) + encodePathSegment(query);
        List<String> readerLinks = searchViaReader(searchUrl);
        if (!readerLinks.isEmpty()) return readerLinks;
        try {
            Document doc = Jsoup.connect(searchUrl)
                .userAgent(USER_AGENT)
                .timeout(timeoutMs)
                .followRedirects(true)
                .get();
            LinkedHashSet<String> links = new LinkedHashSet<>();
            if (PRODUCT_URL.matcher(doc.location()).matches()) links.add(doc.location());
            for (Element anchor : doc.select("a[href]")) {
                String href = anchor.attr("abs:href");
                if (PRODUCT_URL.matcher(href).matches()) links.add(href);
            }
            return List.copyOf(links);
        } catch (IOException ex) {
            log.warn("HTTP error searching supplier for '{}': {}", query, ex.getMessage());
            return List.of();
        }
    }

    private List<String> searchViaReader(String searchUrl) {
        if (readerUrlPrefix == null || readerUrlPrefix.isBlank()) return List.of();
        String target = searchUrl.replaceFirst("^https?://", "");
        try {
            String body = Jsoup.connect(trailingSlash(readerUrlPrefix) + target)
                .userAgent(USER_AGENT)
                .timeout(Math.max(timeoutMs, 30_000))
                .followRedirects(true)
                .ignoreContentType(true)
                .execute()
                .body();
            return extractProductLinks(body);
        } catch (IOException ex) {
            log.warn("Reader fallback failed for '{}': {}", searchUrl, ex.getMessage());
            return List.of();
        }
    }

    List<String> extractProductLinks(String body) {
        if (body == null || body.isBlank()) return List.of();
        LinkedHashSet<String> links = new LinkedHashSet<>();
        java.util.regex.Matcher matcher = PRODUCT_URL_IN_TEXT.matcher(body);
        while (matcher.find()) links.add(matcher.group());
        return List.copyOf(links);
    }

    private Optional<String> searchArchiveIndex(InvoiceItem item) {
        String slug = slug(item.artista() + " " + item.album());
        if (slug.isBlank() || archiveIndexUrl == null || archiveIndexUrl.isBlank()) return Optional.empty();
        throttle();
        try {
            Document doc = Jsoup.connect(trailingSlash(archiveIndexUrl) + slug + "/")
                .userAgent(USER_AGENT)
                .timeout(timeoutMs)
                .followRedirects(true)
                .get();
            List<String> candidates = doc.select("a[href]").stream()
                .map(anchor -> anchor.attr("abs:href"))
                .filter(href -> PRODUCT_URL.matcher(href).matches())
                .distinct()
                .toList();
            return bestCandidate(item, candidates);
        } catch (IOException ex) {
            log.debug("Archive index unavailable for '{}': {}", itemLabel(item), ex.getMessage());
            return Optional.empty();
        }
    }

    Optional<String> bestCandidate(InvoiceItem item, Iterable<String> candidates) {
        List<ScoredUrl> scored = new ArrayList<>();
        for (String candidate : candidates) {
            int score = score(item, candidate);
            if (score >= 250) scored.add(new ScoredUrl(candidate, score));
        }
        return scored.stream()
            .max(Comparator.comparingInt(ScoredUrl::score))
            .map(ScoredUrl::url);
    }

    private int score(InvoiceItem item, String url) {
        String normalizedUrl = normalize(decode(url));
        boolean codeMatch = containsExactToken(normalizedUrl, normalize(item.codigoCatalogo()));
        double artistCoverage = tokenCoverage(item.artista(), normalizedUrl);
        double titleCoverage = tokenCoverage(item.album(), normalizedUrl);
        if (!codeMatch || artistCoverage < 0.5 || titleCoverage < 0.5) return 0;
        return 200 + (int) Math.round(artistCoverage * 50) + (int) Math.round(titleCoverage * 50);
    }

    private double tokenCoverage(String expected, String normalizedCandidate) {
        List<String> tokens = tokens(expected);
        if (tokens.isEmpty()) return 0;
        long matches = tokens.stream().filter(token -> containsExactToken(normalizedCandidate, token)).count();
        return (double) matches / tokens.size();
    }

    private List<String> tokens(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) return List.of();
        return Pattern.compile("\\s+").splitAsStream(normalized)
            .filter(token -> !WEAK_TOKENS.contains(token))
            .distinct()
            .toList();
    }

    private boolean containsExactToken(String normalizedText, String normalizedToken) {
        if (normalizedToken == null || normalizedToken.isBlank()) return false;
        return (" " + normalizedText + " ").contains(" " + normalizedToken + " ");
    }

    private String encodePathSegment(String query) {
        return URLEncoder.encode(query.trim(), StandardCharsets.UTF_8).replace("%2B", "+");
    }

    private String slug(String value) {
        return normalize(value).replace(' ', '-').replaceAll("-+", "-");
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^\\p{L}\\p{N}]+", " ")
            .trim()
            .toLowerCase(Locale.ROOT);
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }

    private String join(String... values) {
        return java.util.Arrays.stream(values)
            .filter(value -> value != null && !value.isBlank())
            .collect(java.util.stream.Collectors.joining(" "));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String trailingSlash(String value) {
        return value.endsWith("/") ? value : value + "/";
    }

    private String itemLabel(InvoiceItem item) {
        return join(item.codigoCatalogo(), item.artista(), item.album());
    }

    private void throttle() {
        if (delayMs <= 0) return;
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private record ScoredUrl(String url, int score) {}
}
