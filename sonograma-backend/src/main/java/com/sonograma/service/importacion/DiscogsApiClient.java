package com.sonograma.service.importacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Component
@Slf4j
public class DiscogsApiClient {

    private static final String USER_AGENT =
            "SonogramaApp/1.0 +https://github.com/sanvieira12/SonogramaProyectoFinal";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Semaphore concurrency;
    private final ImportSession defaultSession = new ImportSession();
    private final Object throttleLock = new Object();

    @Value("${discogs.api.base-url:https://api.discogs.com}")
    private String baseUrl;

    @Value("${discogs.api.token:}")
    private String discogsToken;

    @Value("${discogs.api.request-delay-ms:350}")
    private long requestDelayMs;

    @Value("${discogs.api.max-retries:3}")
    private int maxRetries;

    private long nextRequestAt;

    @Autowired
    public DiscogsApiClient(ObjectMapper objectMapper) {
        this(objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), 2);
    }

    DiscogsApiClient(ObjectMapper objectMapper, HttpClient httpClient, int maxConcurrency) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.concurrency = new Semaphore(maxConcurrency);
    }

    public FetchResult fetch(String type, long id) {
        return fetch(defaultSession, type, id);
    }

    public FetchResult fetch(ImportSession session, String type, long id) {
        String key = type.toLowerCase(Locale.ROOT) + ":" + id;
        Object keyLock = session.keyLocks.computeIfAbsent(key, ignored -> new Object());
        try {
            synchronized (keyLock) {
                FetchResult cached = session.cache.get(key);
                if (cached != null) {
                    log.debug("Discogs cache hit {}", key);
                    return cached.withCacheHit();
                }
                FetchResult result = "master".equalsIgnoreCase(type)
                        ? fetchMaster(session, id)
                        : fetchRelease(session, id, null);
                if (result.success()) {
                    session.cache.putIfAbsent(key, result);
                }
                return result;
            }
        } finally {
            session.keyLocks.remove(key, keyLock);
        }
    }

    public ImportSession newSession() {
        return new ImportSession();
    }

    public int cacheSize() {
        return defaultSession.cache.size();
    }

    void clearCache() {
        defaultSession.cache.clear();
    }

    void configureForTest(String apiBaseUrl, String token, long delayMs, int retries) {
        this.baseUrl = apiBaseUrl;
        this.discogsToken = token;
        this.requestDelayMs = delayMs;
        this.maxRetries = retries;
    }

    private FetchResult fetchMaster(ImportSession session, long masterId) {
        HttpResult masterResponse = request("/masters/" + masterId);
        if (!masterResponse.success()) {
            return FetchResult.failure(masterResponse.rateLimited(), masterResponse.retryAfterMs(),
                    masterResponse.message());
        }
        try {
            JsonNode master = objectMapper.readTree(masterResponse.body());
            long mainRelease = master.path("main_release").asLong(0);
            if (mainRelease <= 0) {
                return FetchResult.failure(false, 0,
                        "El master " + masterId + " no informa main_release");
            }
            FetchResult release = fetchRelease(session, mainRelease, masterId);
            if (release.success()) {
                session.cache.putIfAbsent("release:" + mainRelease, release);
            }
            return release;
        } catch (Exception ex) {
            return FetchResult.failure(false, 0, "Respuesta inválida de Discogs: " + ex.getMessage());
        }
    }

    private FetchResult fetchRelease(ImportSession session, long releaseId, Long masterId) {
        FetchResult cached = session.cache.get("release:" + releaseId);
        if (cached != null) {
            return masterId == null ? cached.withCacheHit() : cached.withMaster(masterId).withCacheHit();
        }
        HttpResult response = request("/releases/" + releaseId);
        if (!response.success()) {
            return FetchResult.failure(response.rateLimited(), response.retryAfterMs(), response.message());
        }
        try {
            JsonNode json = objectMapper.readTree(response.body());
            return new FetchResult(
                    true,
                    false,
                    false,
                    0,
                    null,
                    masterId,
                    releaseId,
                    firstArtist(json),
                    text(json, "title"),
                    positiveInt(json, "year"),
                    firstText(json.path("genres")),
                    firstLabel(json),
                    firstCatalogNumber(json),
                    text(json, "country"),
                    firstText(json.path("styles")),
                    format(json),
                    image(json),
                    null,
                    tracklist(json)
            );
        } catch (Exception ex) {
            return FetchResult.failure(false, 0, "Respuesta inválida de Discogs: " + ex.getMessage());
        }
    }

    private HttpResult request(String path) {
        HttpResult last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            last = requestOnce(path);
            if (!last.rateLimited() || attempt == maxRetries) {
                return last;
            }
            long delay = retryDelay(last.retryAfterMs(), attempt + 1);
            log.warn("Discogs HTTP 429 en {}. Reintento {}/{} en {} ms",
                    path, attempt + 1, maxRetries, delay);
            sleep(delay);
        }
        return last == null ? HttpResult.failure("No se pudo consultar Discogs") : last;
    }

    private HttpResult requestOnce(String path) {
        boolean acquired = false;
        try {
            concurrency.acquire();
            acquired = true;
            throttle();
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .GET();
            if (discogsToken != null && !discogsToken.isBlank()) {
                request.header("Authorization", "Discogs token=" + discogsToken);
            }
            HttpResponse<String> response = httpClient.send(
                    request.build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            applyRateLimitHeaders(response);
            long retryAfterMs = retryAfterMs(response);
            if (response.statusCode() == 429) {
                return HttpResult.rateLimited(retryAfterMs,
                        "Discogs limitó temporalmente las solicitudes (HTTP 429)");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return HttpResult.failure("Discogs devolvió HTTP " + response.statusCode());
            }
            return HttpResult.success(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return HttpResult.failure("Consulta a Discogs interrumpida");
        } catch (Exception ex) {
            log.warn("Error consultando Discogs {}: {}", path, ex.getMessage());
            return HttpResult.failure("Error al consultar Discogs: " + ex.getMessage());
        } finally {
            if (acquired) {
                concurrency.release();
            }
        }
    }

    private long retryDelay(long headerDelay, int retryCount) {
        long exponential = 1_000L * (1L << Math.max(0, retryCount - 1));
        return Math.min(30_000, Math.max(headerDelay, exponential));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void throttle() throws InterruptedException {
        long wait;
        synchronized (throttleLock) {
            long now = System.currentTimeMillis();
            wait = Math.max(0, nextRequestAt - now);
            nextRequestAt = Math.max(now, nextRequestAt) + requestDelayMs;
        }
        if (wait > 0) {
            Thread.sleep(wait);
        }
    }

    private long retryAfterMs(HttpResponse<?> response) {
        Optional<String> retryAfter = response.headers().firstValue("Retry-After");
        if (retryAfter.isPresent()) {
            try {
                return Math.max(1_000, Long.parseLong(retryAfter.get()) * 1_000);
            } catch (NumberFormatException ignored) {
                // Use exponential fallback in the worker.
            }
        }
        Optional<String> remaining = response.headers().firstValue("X-Discogs-Ratelimit-Remaining");
        if (remaining.isPresent() && "0".equals(remaining.get().trim())) {
            return 5_000;
        }
        return 0;
    }

    private void applyRateLimitHeaders(HttpResponse<?> response) {
        Optional<String> remaining = response.headers().firstValue("X-Discogs-Ratelimit-Remaining");
        if (remaining.isEmpty() || !"0".equals(remaining.get().trim())) {
            return;
        }
        long resetMs = response.headers().firstValue("X-Discogs-Ratelimit-Reset")
                .flatMap(this::parseLong)
                .map(seconds -> Math.max(1_000, seconds * 1_000))
                .orElse(5_000L);
        synchronized (throttleLock) {
            nextRequestAt = Math.max(nextRequestAt, System.currentTimeMillis() + resetMs);
        }
    }

    private Optional<Long> parseLong(String value) {
        try {
            return Optional.of(Long.parseLong(value.trim()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private String firstArtist(JsonNode json) {
        JsonNode artists = json.path("artists");
        return artists.isArray() && !artists.isEmpty()
                ? artists.get(0).path("name").asText(null)
                : null;
    }

    private String firstLabel(JsonNode json) {
        JsonNode labels = json.path("labels");
        return labels.isArray() && !labels.isEmpty()
                ? labels.get(0).path("name").asText(null)
                : null;
    }

    private String firstCatalogNumber(JsonNode json) {
        JsonNode labels = json.path("labels");
        return labels.isArray() && !labels.isEmpty()
                ? text(labels.get(0), "catno")
                : null;
    }

    private String firstText(JsonNode array) {
        return array.isArray() && !array.isEmpty() ? array.get(0).asText(null) : null;
    }

    private String text(JsonNode json, String field) {
        String value = json.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private Integer positiveInt(JsonNode json, String field) {
        int value = json.path(field).asInt(0);
        return value > 0 ? value : null;
    }

    private String format(JsonNode json) {
        JsonNode formats = json.path("formats");
        if (!formats.isArray() || formats.isEmpty()) return "VINILO";
        String name = formats.get(0).path("name").asText("").toUpperCase(Locale.ROOT);
        if (name.contains("CD")) return "CD";
        if (name.contains("CASSETTE")) return "CASSETTE";
        return "VINILO";
    }

    private String image(JsonNode json) {
        JsonNode images = json.path("images");
        if (!images.isArray()) return null;
        for (JsonNode image : images) {
            if ("primary".equals(image.path("type").asText())) {
                return image.path("uri").asText(null);
            }
        }
        return images.isEmpty() ? null : images.get(0).path("uri").asText(null);
    }

    private String tracklist(JsonNode json) {
        JsonNode tracks = json.path("tracklist");
        if (!tracks.isArray() || tracks.isEmpty()) return null;
        StringBuilder value = new StringBuilder();
        for (JsonNode track : tracks) {
            if (!value.isEmpty()) value.append("\n");
            String position = track.path("position").asText("");
            if (!position.isBlank()) value.append(position).append(". ");
            value.append(track.path("title").asText(""));
        }
        return value.toString();
    }

    private record HttpResult(
            boolean success,
            boolean rateLimited,
            long retryAfterMs,
            String body,
            String message
    ) {
        static HttpResult success(String body) {
            return new HttpResult(true, false, 0, body, null);
        }

        static HttpResult failure(String message) {
            return new HttpResult(false, false, 0, null, message);
        }

        static HttpResult rateLimited(long retryAfterMs, String message) {
            return new HttpResult(false, true, retryAfterMs, null, message);
        }
    }

    public record FetchResult(
            boolean success,
            boolean rateLimited,
            boolean cacheHit,
            long retryAfterMs,
            String errorMessage,
            Long masterId,
            Long resolvedReleaseId,
            String artist,
            String title,
            Integer year,
            String genre,
            String label,
            String catalogNumber,
            String country,
            String style,
            String format,
            String imageUrl,
            String previewUrl,
            String tracklist
    ) {
        static FetchResult failure(boolean rateLimited, long retryAfterMs, String message) {
            return new FetchResult(false, rateLimited, false, retryAfterMs, message,
                    null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        FetchResult withCacheHit() {
            return new FetchResult(success, rateLimited, true, retryAfterMs, errorMessage,
                    masterId, resolvedReleaseId, artist, title, year, genre, label, country,
                    catalogNumber, style, format, imageUrl, previewUrl, tracklist);
        }

        FetchResult withMaster(Long newMasterId) {
            return new FetchResult(success, rateLimited, cacheHit, retryAfterMs, errorMessage,
                    newMasterId, resolvedReleaseId, artist, title, year, genre, label, country,
                    catalogNumber, style, format, imageUrl, previewUrl, tracklist);
        }
    }

    public static final class ImportSession {
        private final Map<String, FetchResult> cache = new ConcurrentHashMap<>();
        private final Map<String, Object> keyLocks = new ConcurrentHashMap<>();
    }
}
