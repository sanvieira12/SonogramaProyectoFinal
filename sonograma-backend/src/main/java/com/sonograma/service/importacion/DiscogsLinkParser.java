package com.sonograma.service.importacion;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DiscogsLinkParser {

    private static final Pattern DISCOGS_URL = Pattern.compile(
            "(?i)(?:https?://)?(?:www\\.)?discogs\\.com/(?:[a-z]{2}/)?(?:sell/)?(release|master)/(\\d+)(?:[^\\s<>\"]*)"
    );
    private static final Pattern BRACKET_ID = Pattern.compile("(?i)\\[\\s*([rm])(\\d+)\\s*]");
    private static final Pattern PLAIN_TYPED_ID = Pattern.compile("(?i)(?:^|\\b)(release|master)\\s*/\\s*(\\d+)\\b");
    private static final Pattern PLAIN_PREFIX_ID = Pattern.compile("(?i)(?:^|\\b)([rm])(\\d{3,})\\b");

    public Optional<DiscogsLink> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        Optional<DiscogsLink> marketplaceMaster = parseMarketplaceMasterUrl(trimmed);
        if (marketplaceMaster.isPresent()) {
            return marketplaceMaster;
        }

        Matcher matcher = DISCOGS_URL.matcher(trimmed);
        if (matcher.find()) {
            return Optional.of(link(matcher.group(1), matcher.group(2), trimTrailingPunctuation(matcher.group())));
        }

        matcher = BRACKET_ID.matcher(trimmed);
        if (matcher.find()) {
            return Optional.of(link(typeFromPrefix(matcher.group(1)), matcher.group(2), matcher.group()));
        }

        matcher = PLAIN_TYPED_ID.matcher(trimmed);
        if (matcher.find()) {
            return Optional.of(link(matcher.group(1), matcher.group(2), matcher.group()));
        }

        matcher = PLAIN_PREFIX_ID.matcher(trimmed);
        if (matcher.find()) {
            return Optional.of(link(typeFromPrefix(matcher.group(1)), matcher.group(2), matcher.group()));
        }

        return Optional.empty();
    }

    private Optional<DiscogsLink> parseMarketplaceMasterUrl(String value) {
        String candidate = trimTrailingPunctuation(value);
        String uriValue = candidate.matches("(?i)^https?://.*")
                ? candidate
                : "https://" + candidate;

        try {
            URI uri = URI.create(uriValue);
            if (!isAcceptedDiscogsHost(uri) || !isMarketplaceListPath(uri.getPath())) {
                return Optional.empty();
            }

            String query = uri.getRawQuery();
            if (query == null || query.isBlank()) {
                return Optional.empty();
            }

            Long masterId = null;
            for (String parameter : query.split("&", -1)) {
                int separator = parameter.indexOf('=');
                if (separator < 0) {
                    continue;
                }
                String key = parameter.substring(0, separator);
                if (!"master_id".equalsIgnoreCase(key)) {
                    continue;
                }
                if (masterId != null) {
                    return Optional.empty();
                }
                String rawId = parameter.substring(separator + 1);
                if (!rawId.matches("[1-9]\\d*")) {
                    return Optional.empty();
                }
                try {
                    masterId = Long.parseLong(rawId);
                } catch (NumberFormatException ignored) {
                    return Optional.empty();
                }
            }

            return masterId == null
                    ? Optional.empty()
                    : Optional.of(link("master", masterId.toString(), candidate));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private boolean isAcceptedDiscogsHost(URI uri) {
        if (uri.getUserInfo() != null || uri.getHost() == null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return "discogs.com".equals(host) || "www.discogs.com".equals(host);
    }

    private boolean isMarketplaceListPath(String path) {
        if (path == null) {
            return false;
        }
        String normalizedPath = path.replaceFirst("/+$", "");
        return normalizedPath.matches("(?i)/(?:[a-z]{2}/)?sell/list");
    }

    private DiscogsLink link(String rawType, String rawId, String original) {
        String type = rawType.toLowerCase(Locale.ROOT);
        long id = Long.parseLong(rawId);
        return new DiscogsLink(
                type,
                id,
                original,
                "https://www.discogs.com/" + type + "/" + id
        );
    }

    private String typeFromPrefix(String prefix) {
        return "m".equalsIgnoreCase(prefix) ? "master" : "release";
    }

    private String trimTrailingPunctuation(String value) {
        return value.replaceFirst("[),.;]+$", "");
    }

    public record DiscogsLink(String type, long id, String originalUrl, String normalizedUrl) {}
}
