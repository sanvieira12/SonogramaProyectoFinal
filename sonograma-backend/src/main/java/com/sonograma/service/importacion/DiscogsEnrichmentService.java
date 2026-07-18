package com.sonograma.service.importacion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * The single Discogs enrichment boundary used by both the one-link and Excel imports.
 * It owns link parsing, release/master resolution, cover retrieval and the API session.
 */
@Service
@RequiredArgsConstructor
public class DiscogsEnrichmentService {

    private final DiscogsLinkParser linkParser;
    private final DiscogsApiClient apiClient;
    private final DiscogsCoverService coverService;

    public EnrichmentResult enrich(String url, DiscogsApiClient.ImportSession session) {
        Optional<DiscogsLinkParser.DiscogsLink> link = linkParser.parse(url);
        if (link.isEmpty()) {
            return EnrichmentResult.failure("No se pudo extraer un ID release/master de la URL: " + url);
        }
        return enrich(link.get(), session);
    }

    public EnrichmentResult enrich(DiscogsLinkParser.DiscogsLink link,
                                   DiscogsApiClient.ImportSession session) {
        DiscogsApiClient.FetchResult result = apiClient.fetch(session, link.type(), link.id());
        if (!result.success()) {
            return EnrichmentResult.failure(result.errorMessage(), result);
        }
        DiscogsCoverService.CoverResult cover = result.resolvedReleaseId() == null
                ? DiscogsCoverService.CoverResult.missing("No se pudo resolver el release")
                : coverService.download(result.imageUrl(), result.resolvedReleaseId());
        return EnrichmentResult.success(result, link.normalizedUrl(), cover);
    }

    public record EnrichmentResult(
            boolean success,
            String errorMessage,
            DiscogsApiClient.FetchResult metadata,
            String normalizedUrl,
            DiscogsCoverService.CoverResult cover
    ) {
        static EnrichmentResult success(DiscogsApiClient.FetchResult metadata,
                                        String normalizedUrl,
                                        DiscogsCoverService.CoverResult cover) {
            return new EnrichmentResult(true, null, metadata, normalizedUrl, cover);
        }

        static EnrichmentResult failure(String errorMessage) {
            return failure(errorMessage, null);
        }

        static EnrichmentResult failure(String errorMessage, DiscogsApiClient.FetchResult metadata) {
            return new EnrichmentResult(false, errorMessage, metadata, null,
                    DiscogsCoverService.CoverResult.missing(errorMessage));
        }
    }
}
