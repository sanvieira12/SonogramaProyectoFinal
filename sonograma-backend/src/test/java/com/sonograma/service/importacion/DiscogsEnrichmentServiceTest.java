package com.sonograma.service.importacion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiscogsEnrichmentServiceTest {

    @Test
    void preservesFullReleaseUrlWhenItMatchesResolvedReleaseIdentity() {
        DiscogsApiClient apiClient = mock(DiscogsApiClient.class);
        DiscogsCoverService coverService = mock(DiscogsCoverService.class);
        DiscogsEnrichmentService service = new DiscogsEnrichmentService(
                new DiscogsLinkParser(), apiClient, coverService);
        DiscogsApiClient.FetchResult result = new DiscogsApiClient.FetchResult(
                true, false, false, 0, null, null, 11272493L,
                "ZP", "Tracid", 2001, "Electronic", null, null, null, null,
                "VINYL", null, null, null, List.of());
        when(apiClient.fetch(any(), eq("release"), eq(11272493L))).thenReturn(result);
        when(coverService.download(null, 11272493L))
                .thenReturn(DiscogsCoverService.CoverResult.missing("no cover"));

        DiscogsEnrichmentService.EnrichmentResult enriched = service.enrich(
                "https://www.discogs.com/es/release/11272493-ZP-Tracid",
                apiClient.newSession());

        assertThat(enriched.normalizedUrl())
                .isEqualTo("https://www.discogs.com/es/release/11272493-ZP-Tracid");
    }
}
