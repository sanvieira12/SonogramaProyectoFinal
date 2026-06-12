package com.sonograma.service.importacion;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DiscogsLinkParserTest {

    private final DiscogsLinkParser parser = new DiscogsLinkParser();

    @ParameterizedTest
    @CsvSource({
            "https://www.discogs.com/release/11-title,release,11",
            "http://discogs.com/es/release/22,release,22",
            "discogs.com/master/33-name,master,33",
            "www.discogs.com/es/master/44,master,44",
            "https://www.discogs.com/release/55-title/?utm_source=test,release,55",
            "'https://discogs.com/master/66-title/).',master,66"
    })
    void parsesSupportedUrlVariants(String url, String type, long id) {
        var parsed = parser.parse(url).orElseThrow();
        assertThat(parsed.type()).isEqualTo(type);
        assertThat(parsed.id()).isEqualTo(id);
        assertThat(parsed.normalizedUrl()).isEqualTo("https://www.discogs.com/" + type + "/" + id);
    }
}
