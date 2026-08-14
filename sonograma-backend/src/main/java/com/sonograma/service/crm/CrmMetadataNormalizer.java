package com.sonograma.service.crm;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CrmMetadataNormalizer {

    private static final Pattern MULTI_SEPARATOR = Pattern.compile("\\s*[,;/|]+\\s*");
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(19|20)\\d{2}(?!\\d)");
    private static final Pattern SHORT_DECADE = Pattern.compile("(?<!\\d)(\\d{2})\\s*'?s(?!\\w)");
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "al", "algo", "anything", "busca", "buscando", "busco", "by", "con", "cualquier",
            "de", "del", "el", "en", "for", "from", "interesa", "interesado", "interesada", "la", "las",
            "lo", "los", "looking", "me", "original", "originales", "para", "por", "pressing", "pressings",
            "que", "quiero", "the", "un", "una", "y"
    );

    private CrmMetadataNormalizer() {}

    public static String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('&', ' ')
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        return normalized;
    }

    public static List<Token> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        LinkedHashMap<String, Token> unique = new LinkedHashMap<>();
        for (String raw : MULTI_SEPARATOR.split(value.trim())) {
            String label = raw.trim().replaceAll("\\s+", " ");
            String key = normalize(label);
            if (!key.isBlank()) unique.putIfAbsent(key, new Token(key, label));
        }
        return List.copyOf(unique.values());
    }

    public static Optional<Token> single(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        String label = value.trim().replaceAll("\\s+", " ");
        String key = normalize(label);
        return key.isBlank() ? Optional.empty() : Optional.of(new Token(key, label));
    }

    public static Optional<Token> format(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) return Optional.empty();
        String compact = normalized.replace(" ", "");
        if (compact.matches(".*([3-9]x(lp|vinyl)).*")) {
            Matcher matcher = Pattern.compile("([3-9])x(?:lp|vinyl)").matcher(compact);
            if (matcher.find()) return Optional.of(new Token(matcher.group(1) + "xlp", matcher.group(1) + "xLP"));
        }
        if (compact.contains("2xlp") || compact.contains("2xvinyl") || normalized.contains("double lp")) {
            return Optional.of(new Token("2xlp", "2xLP"));
        }
        if (normalized.matches(".*(^| )12( inch)?($| ).*") || value.contains("12\"")) {
            return Optional.of(new Token("12", "12\"") );
        }
        if (normalized.matches(".*(^| )10( inch)?($| ).*") || value.contains("10\"")) {
            return Optional.of(new Token("10", "10\"") );
        }
        if (normalized.matches(".*(^| )7( inch)?($| ).*") || value.contains("7\"")) {
            return Optional.of(new Token("7", "7\"") );
        }
        if (normalized.matches(".*(^| )(lp|vinyl)($| ).*")) return Optional.of(new Token("lp", "LP"));
        return single(value);
    }

    public static String decade(Integer year) {
        if (year == null || year < 1000 || year > 9999) return null;
        int start = year / 10 * 10;
        return start + "–" + (start + 9);
    }

    public static Set<String> meaningfulTerms(String text) {
        String normalized = normalize(expandDecades(text));
        if (normalized.isBlank()) return Set.of();
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String term : normalized.split(" ")) {
            if (term.length() > 1 && !STOP_WORDS.contains(term)) terms.add(countryAlias(term));
        }
        return terms;
    }

    public static String expandDecades(String text) {
        if (text == null) return "";
        String expanded = text;
        Matcher shortMatcher = SHORT_DECADE.matcher(expanded.toLowerCase(Locale.ROOT));
        StringBuffer buffer = new StringBuffer();
        while (shortMatcher.find()) {
            int shortYear = Integer.parseInt(shortMatcher.group(1));
            int start = shortYear >= 30 ? 1900 + shortYear : 2000 + shortYear;
            shortMatcher.appendReplacement(buffer, start + " " + (start + 9));
        }
        shortMatcher.appendTail(buffer);
        expanded = buffer.toString();
        Matcher spanishMatcher = Pattern.compile("(?i)a(?:ñ|n)os?\\s+(\\d{2})(?!\\d)").matcher(expanded);
        buffer = new StringBuffer();
        while (spanishMatcher.find()) {
            int shortYear = Integer.parseInt(spanishMatcher.group(1));
            int start = shortYear >= 30 ? 1900 + shortYear : 2000 + shortYear;
            spanishMatcher.appendReplacement(buffer, start + " " + (start + 9));
        }
        spanishMatcher.appendTail(buffer);
        return buffer.toString();
    }

    public static Set<Integer> mentionedYears(String text) {
        LinkedHashSet<Integer> years = new LinkedHashSet<>();
        Matcher matcher = YEAR.matcher(expandDecades(text));
        while (matcher.find()) years.add(Integer.parseInt(matcher.group()));
        return years;
    }

    private static String countryAlias(String term) {
        return switch (term) {
            case "aleman", "alemana", "alemania", "german" -> "germany";
            case "ingles", "inglesa", "british", "reino", "unido" -> "uk";
            case "estadounidense", "americano", "americana" -> "usa";
            default -> term;
        };
    }

    public record Token(String key, String label) {}
}
