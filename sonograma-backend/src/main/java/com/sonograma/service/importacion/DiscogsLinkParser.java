package com.sonograma.service.importacion;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DiscogsLinkParser {

    private static final Pattern DISCOGS_URL = Pattern.compile(
            "(?i)(?:https?://)?(?:www\\.)?discogs\\.com/(?:[a-z]{2}/)?(release|master)/(\\d+)(?:[^\\s]*)"
    );

    public Optional<DiscogsLink> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = DISCOGS_URL.matcher(value.trim());
        if (!matcher.find()) {
            return Optional.empty();
        }
        String type = matcher.group(1).toLowerCase(Locale.ROOT);
        long id = Long.parseLong(matcher.group(2));
        return Optional.of(new DiscogsLink(
                type,
                id,
                matcher.group(),
                "https://www.discogs.com/" + type + "/" + id
        ));
    }

    public record DiscogsLink(String type, long id, String originalUrl, String normalizedUrl) {}
}
