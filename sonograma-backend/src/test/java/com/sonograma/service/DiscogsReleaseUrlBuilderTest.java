package com.sonograma.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscogsReleaseUrlBuilderTest {

    @Test
    void preservesAValidatedDescriptiveReleaseUrlForTheAuthoritativeRelease() {
        String stored = "https://www.discogs.com/es/release/195695-Rififi-Dr-Acid-And-Mr-House";

        assertThat(DiscogsReleaseUrlBuilder.build(195695L, stored, "Ignored", "Ignored"))
                .isEqualTo(stored);
    }

    @Test
    void rejectsMasterExternalMalformedAndMismatchedUrlsAndKeepsReleaseIdentity() {
        assertThat(DiscogsReleaseUrlBuilder.build(
                195695L, "https://www.discogs.com/master/179057", "Rififi", "Dr. Acid And Mr. House"))
                .isEqualTo("https://www.discogs.com/release/195695-Rififi-Dr-Acid-And-Mr-House");
        assertThat(DiscogsReleaseUrlBuilder.build(
                195695L, "https://www.discogs.com/release/999-Wrong", "Rififi", "Dr. Acid And Mr. House"))
                .startsWith("https://www.discogs.com/release/195695-");
        assertThat(DiscogsReleaseUrlBuilder.build(
                195695L, "https://example.com/release/195695", "Rififi", "Dr. Acid And Mr. House"))
                .startsWith("https://www.discogs.com/release/195695-");
        assertThat(DiscogsReleaseUrlBuilder.build(195695L, "not a uri", null, null))
                .isEqualTo("https://www.discogs.com/release/195695");
    }

    @Test
    void slugIsDeterministicUrlSafeAndUnicodeTolerant() {
        assertThat(DiscogsReleaseUrlBuilder.slug("  Björk && 東京 ", "Álbum: Uno!!!"))
                .isEqualTo("Bjork-%E6%9D%B1%E4%BA%AC-Album-Uno");
        assertThat(DiscogsReleaseUrlBuilder.slug("---", "...")).isEmpty();
    }
}
