package com.sonograma.service;

import com.sonograma.dto.TrackInfo;
import com.sonograma.dto.VinylPageData;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrapes a vinylfuture.com product page for cover image and track list.
 *
 * VinylFuture product URLs embed the numeric product ID: ...Vinyl__{numericId}
 * That ID drives asset URLs on deejay.de (VinylFuture's backend):
 *
 *   Cover (XL): https://www.deejay.de/images/xl/{id[-2]}/{id[-1]}/{id}.jpg
 *   Track MP3:  https://www.deejay.de/streamit/{id[-2]}/{id[-1]}/{id}{letter}.mp3
 *
 * Track data is parsed from the tracklist HTML:
 *   <a id="playTrack_{id}_{letter}"><b>A1</b> <span class="trackname">Title</span></a>
 */
@Slf4j
@Service
public class VinylFutureScraperService {

    private static final String DEEJAY_BASE = "https://www.deejay.de";
    private static final String USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final Pattern PRODUCT_ID = Pattern.compile("__(\\d+)$");

    @Value("${sonograma.vinylfuture.timeout-ms:10000}")
    private int timeoutMs;

    public Optional<VinylPageData> scrape(String productUrl) {
        Matcher m = PRODUCT_ID.matcher(productUrl);
        if (!m.find()) {
            log.warn("Cannot extract product ID from URL: {}", productUrl);
            return Optional.empty();
        }
        String id = m.group(1);

        Document doc;
        try {
            doc = Jsoup.connect(productUrl)
                .userAgent(USER_AGENT)
                .timeout(timeoutMs)
                .followRedirects(true)
                .get();
        } catch (Exception e) {
            log.warn("Cannot fetch '{}': {}", productUrl, e.getMessage());
            return Optional.empty();
        }

        String imageUrl = buildImageUrl(id);
        List<TrackInfo> tracks = extractTracks(doc, id);

        log.debug("Scraped '{}' (id={}): tracks={}", productUrl, id, tracks.size());
        return Optional.of(new VinylPageData(imageUrl, null, tracks));
    }

    /**
     * XL cover image: last two digits of ID form the subdirectory levels.
     * Example: id=106278 → /images/xl/7/8/106278.jpg
     */
    private String buildImageUrl(String id) {
        char c2 = id.charAt(id.length() - 2);
        char c1 = id.charAt(id.length() - 1);
        return DEEJAY_BASE + "/images/xl/" + c2 + "/" + c1 + "/" + id + ".jpg";
    }

    /**
     * Parses the tracklist. Each track element:
     *   <a id="playTrack_{id}_{letter}"><b>A1</b> <span class="trackname">Title</span></a>
     *
     * MP3 URL: https://www.deejay.de/streamit/{id[-2]}/{id[-1]}/{id}{letter}.mp3
     */
    private List<TrackInfo> extractTracks(Document doc, String id) {
        List<TrackInfo> tracks = new ArrayList<>();

        char c2 = id.charAt(id.length() - 2);
        char c1 = id.charAt(id.length() - 1);
        String streamBase = DEEJAY_BASE + "/streamit/" + c2 + "/" + c1 + "/" + id;
        String prefix = "playTrack_" + id + "_";

        for (Element a : doc.select("a[id^=" + prefix + "]")) {
            String letter = a.id().substring(prefix.length());

            Element bold = a.selectFirst("b");
            String label = bold != null ? bold.text().strip() : letter.toUpperCase();

            Element nameSpan = a.selectFirst(".trackname");
            String name = nameSpan != null ? nameSpan.text().strip() : "";

            String mp3Url = streamBase + letter + ".mp3";
            tracks.add(new TrackInfo(label, name, mp3Url));
        }

        return tracks;
    }
}
