package com.sonograma.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonograma.dto.TrackInfo;
import com.sonograma.dto.VinylPageData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class VinylFutureScraperService {

    private static final String DEEJAY_BASE = "https://www.deejay.de";
    private static final String USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final Pattern PRODUCT_ID = Pattern.compile("(?:__|/)(\\d+)(?:\\D*$|$)");
    private static final Pattern MP3_URL = Pattern.compile(
        "(?i)(https?:)?(?:\\\\?/\\\\?/|/)?[^\\s\"'<>\\\\]+\\.mp3(?:\\?[^\\s\"'<>\\\\]*)?"
    );
    private static final Pattern TRACK_POSITION = Pattern.compile("(?i)\\b([A-H]\\d{1,2})\\b");
    private static final Pattern YEAR = Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b");
    private static final Pattern PRICE = Pattern.compile("([0-9]+(?:[.,][0-9]{1,2})?)");

    private final ObjectMapper objectMapper;
    private final Map<String, Optional<VinylPageData>> pageCache = new ConcurrentHashMap<>();

    @Value("${sonograma.vinylfuture.timeout-ms:10000}")
    private int timeoutMs;

    public Optional<VinylPageData> scrape(String productUrl) {
        if (productUrl == null || productUrl.isBlank()) return Optional.empty();
        return pageCache.computeIfAbsent(productUrl, this::scrapeSinCache);
    }

    private Optional<VinylPageData> scrapeSinCache(String productUrl) {
        try {
            Document doc = Jsoup.connect(productUrl)
                .userAgent(USER_AGENT)
                .timeout(timeoutMs)
                .followRedirects(true)
                .get();
            VinylPageData data = extractPageData(doc, productUrl);
            log.debug("Scraped '{}': tracks={}, title='{}'", productUrl, data.tracks().size(), data.title());
            return Optional.of(data);
        } catch (Exception e) {
            log.warn("Cannot fetch '{}': {}", productUrl, e.getMessage());
            return Optional.empty();
        }
    }

    VinylPageData extractPageData(Document doc, String productUrl) {
        Map<String, String> jsonLd = extractJsonLd(doc);
        Map<String, String> labelled = extractLabelledValues(doc);
        String productId = extractProductId(productUrl).orElse(null);

        List<TrackInfo> tracks = extractTracks(doc, productUrl, productId);
        String image = firstNonBlank(
            meta(doc, "meta[property=og:image]", "content"),
            meta(doc, "meta[itemprop=image]", "content"),
            jsonLd.get("image"),
            productId != null ? buildImageUrl(productId) : null
        );

        String pageTitle = firstNonBlank(
            meta(doc, "meta[property=og:title]", "content"),
            jsonLd.get("name"),
            text(doc, "h1"),
            doc.title()
        );
        String artist = firstNonBlank(
            labelled.get("artist"), labelled.get("artista"),
            meta(doc, "meta[name=music:musician]", "content"),
            text(doc, "[itemprop=byArtist]"), text(doc, ".artist")
        );
        String title = firstNonBlank(
            labelled.get("title"), labelled.get("titulo"), labelled.get("album"),
            jsonLd.get("name"), text(doc, "[itemprop=name]"), pageTitle
        );

        return new VinylPageData(
            productUrl,
            clean(artist),
            clean(title),
            clean(firstNonBlank(labelled.get("catalog"), labelled.get("catalogue"), labelled.get("codigo"),
                labelled.get("cat no"), labelled.get("cat. no"), labelled.get("barcode"), jsonLd.get("sku"))),
            clean(firstNonBlank(labelled.get("label"), labelled.get("sello"), text(doc, ".label"))),
            clean(firstNonBlank(labelled.get("genre"), labelled.get("genero"), text(doc, ".genre"))),
            parseYear(firstNonBlank(labelled.get("year"), labelled.get("anio"), labelled.get("released"),
                jsonLd.get("datePublished"))),
            clean(firstNonBlank(labelled.get("country"), labelled.get("pais"))),
            clean(firstNonBlank(labelled.get("format"), labelled.get("formato"))),
            clean(firstNonBlank(labelled.get("condition"), labelled.get("condicion"))),
            clean(firstNonBlank(meta(doc, "meta[property=og:description]", "content"),
                meta(doc, "meta[name=description]", "content"), jsonLd.get("description"),
                labelled.get("description"), labelled.get("descripcion"), labelled.get("notes"), labelled.get("notas"))),
            parsePrice(firstNonBlank(jsonLd.get("price"), labelled.get("price"), labelled.get("precio"))),
            resolveUrl(productUrl, image),
            resolveUrl(productUrl, firstNonBlank(labelled.get("back image"), labelled.get("back cover"))),
            tracks
        );
    }

    private List<TrackInfo> extractTracks(Document doc, String productUrl, String productId) {
        Map<String, TrackInfo> tracks = new LinkedHashMap<>();

        for (Element element : doc.select(
            "audio[src], source[src], a[href], [data-src], [data-audio], [data-mp3], [data-preview], [data-url]"
        )) {
            for (String attr : List.of("src", "href", "data-src", "data-audio", "data-mp3", "data-preview", "data-url")) {
                addTrack(tracks, productUrl, element.attr(attr), positionOf(element), nameOf(element));
            }
        }

        Matcher matcher = MP3_URL.matcher(doc.html().replace("\\/", "/"));
        while (matcher.find()) {
            String raw = matcher.group();
            addTrack(tracks, productUrl, raw, null, null);
        }

        if (productId != null) {
            String prefix = "playTrack_" + productId + "_";
            String streamBase = streamBase(productId);
            for (Element anchor : doc.select("a[id^=" + prefix + "]")) {
                String suffix = anchor.id().substring(prefix.length());
                addTrack(tracks, productUrl, streamBase + suffix + ".mp3", positionOf(anchor), nameOf(anchor));
            }
        }

        return new ArrayList<>(tracks.values());
    }

    private void addTrack(Map<String, TrackInfo> tracks, String pageUrl, String rawUrl,
                          String position, String name) {
        if (rawUrl == null || rawUrl.isBlank() || !rawUrl.toLowerCase(Locale.ROOT).contains(".mp3")) return;
        String resolved = resolveUrl(pageUrl, rawUrl.replace("\\/", "/").replace("&amp;", "&"));
        if (resolved == null) return;
        tracks.merge(resolved, new TrackInfo(clean(position), clean(name), resolved), this::preferDetailedTrack);
    }

    private TrackInfo preferDetailedTrack(TrackInfo current, TrackInfo candidate) {
        return new TrackInfo(
            firstNonBlank(current.label(), candidate.label()),
            firstNonBlank(current.name(), candidate.name()),
            current.mp3Url(),
            firstNonBlank(current.youtubeUrl(), candidate.youtubeUrl())
        );
    }

    private String positionOf(Element element) {
        String explicit = firstNonBlank(element.attr("data-position"), element.attr("data-track"),
            element.selectFirst(".position") != null ? element.selectFirst(".position").text() : null,
            element.selectFirst("b") != null ? element.selectFirst("b").text() : null);
        Matcher matcher = TRACK_POSITION.matcher(firstNonBlank(explicit, element.text(), ""));
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : clean(explicit);
    }

    private String nameOf(Element element) {
        Element name = element.selectFirst(".trackname, .track-name, [itemprop=name]");
        String value = name != null ? name.text() : element.attr("data-title");
        if (value == null || value.isBlank()) {
            value = element.ownText();
        }
        String position = positionOf(element);
        return position == null ? clean(value) : clean(value.replaceFirst("(?i)^\\s*" + Pattern.quote(position), ""));
    }

    private Map<String, String> extractLabelledValues(Document doc) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Element row : doc.select("tr")) {
            List<Element> cells = row.select("th,td");
            if (cells.size() >= 2) putLabel(values, cells.get(0).text(), cells.get(1).text());
        }
        for (Element dt : doc.select("dt")) {
            Element dd = dt.nextElementSibling();
            if (dd != null && "dd".equals(dd.tagName())) putLabel(values, dt.text(), dd.text());
        }
        for (Element element : doc.select("[data-label]")) {
            putLabel(values, element.attr("data-label"), element.text());
        }
        return values;
    }

    private void putLabel(Map<String, String> values, String label, String value) {
        if (label == null || value == null || value.isBlank()) return;
        values.putIfAbsent(normalizeLabel(label), value.strip());
    }

    private String normalizeLabel(String label) {
        return label.toLowerCase(Locale.ROOT).replace(":", "").strip();
    }

    private Map<String, String> extractJsonLd(Document doc) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Element script : doc.select("script[type=application/ld+json]")) {
            try {
                collectJsonLd(objectMapper.readTree(script.data()), values);
            } catch (Exception ignored) {
                log.debug("Invalid JSON-LD ignored");
            }
        }
        return values;
    }

    private void collectJsonLd(JsonNode node, Map<String, String> values) {
        if (node == null) return;
        if (node.isArray()) {
            node.forEach(child -> collectJsonLd(child, values));
            return;
        }
        if (!node.isObject()) return;
        copyText(node, values, "name", "description", "sku", "datePublished", "price");
        JsonNode image = node.get("image");
        if (image != null) {
            if (image.isTextual()) values.putIfAbsent("image", image.asText());
            else if (image.isArray() && !image.isEmpty()) values.putIfAbsent("image", image.get(0).asText());
            else if (image.has("url")) values.putIfAbsent("image", image.get("url").asText());
        }
        if (node.has("offers")) collectJsonLd(node.get("offers"), values);
        if (node.has("@graph")) collectJsonLd(node.get("@graph"), values);
    }

    private void copyText(JsonNode node, Map<String, String> values, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isValueNode() && !value.asText().isBlank()) {
                values.putIfAbsent(key, value.asText());
            }
        }
    }

    private Optional<String> extractProductId(String productUrl) {
        Matcher matcher = PRODUCT_ID.matcher(productUrl);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private String buildImageUrl(String id) {
        if (id.length() < 2) return null;
        return DEEJAY_BASE + "/images/xl/" + id.charAt(id.length() - 2) + "/"
            + id.charAt(id.length() - 1) + "/" + id + ".jpg";
    }

    private String streamBase(String id) {
        return DEEJAY_BASE + "/streamit/" + id.charAt(id.length() - 2) + "/"
            + id.charAt(id.length() - 1) + "/" + id;
    }

    private String resolveUrl(String pageUrl, String value) {
        if (value == null || value.isBlank() || value.startsWith("data:")) return value;
        try {
            if (value.startsWith("//")) return URI.create(pageUrl).getScheme() + ":" + value;
            return URI.create(pageUrl).resolve(value).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String meta(Document doc, String selector, String attr) {
        Element element = doc.selectFirst(selector);
        return element != null ? element.attr(attr) : null;
    }

    private String text(Document doc, String selector) {
        Element element = doc.selectFirst(selector);
        return element != null ? element.text() : null;
    }

    private Integer parseYear(String value) {
        if (value == null) return null;
        Matcher matcher = YEAR.matcher(value);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private BigDecimal parsePrice(String value) {
        if (value == null) return null;
        Matcher matcher = PRICE.matcher(value.replace(" ", ""));
        if (!matcher.find()) return null;
        try {
            return new BigDecimal(matcher.group(1).replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
