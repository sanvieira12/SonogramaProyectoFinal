package com.sonograma.service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds deterministic release URLs exclusively from persisted local facts. */
public final class DiscogsReleaseUrlBuilder {

    private static final Pattern RELEASE_PATH = Pattern.compile(
            "^/(?:[a-z]{2}/)?release/(\\d+)(?:-([^/?#]+))?/?$",
            Pattern.CASE_INSENSITIVE);

    private DiscogsReleaseUrlBuilder() {}

    public static String build(Long authoritativeReleaseId, String storedUrl, String artist, String title) {
        ValidReleaseUrl stored = validateStoredRelease(storedUrl);
        Long releaseId = authoritativeReleaseId != null
                ? authoritativeReleaseId
                : stored == null ? null : stored.releaseId();
        if (releaseId == null || releaseId <= 0) return null;

        if (stored != null && stored.releaseId().equals(releaseId) && stored.descriptive()) {
            return stored.url();
        }

        String slug = slug(artist, title);
        return slug.isBlank()
                ? "https://www.discogs.com/release/" + releaseId
                : "https://www.discogs.com/release/" + releaseId + "-" + slug;
    }

    static String slug(String artist, String title) {
        String raw = String.join(" ", value(artist), value(title)).trim();
        if (raw.isBlank()) return "";
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        if (normalized.isBlank()) return "";
        String bounded = normalized.codePoints().limit(180)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
                .replaceAll("-$", "");
        return URLEncoder.encode(bounded, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static ValidReleaseUrl validateStoredRelease(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null
                    || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))
                    || !(host.equalsIgnoreCase("discogs.com") || host.equalsIgnoreCase("www.discogs.com"))) {
                return null;
            }
            Matcher matcher = RELEASE_PATH.matcher(uri.getPath());
            if (!matcher.matches()) return null;
            Long releaseId = Long.valueOf(matcher.group(1));
            boolean descriptive = matcher.group(2) != null && !matcher.group(2).isBlank();
            return new ValidReleaseUrl(value.trim(), releaseId, descriptive);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }

    private record ValidReleaseUrl(String url, Long releaseId, boolean descriptive) {}
}
