package com.sonograma.service.importacion;

import org.springframework.stereotype.Component;

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
