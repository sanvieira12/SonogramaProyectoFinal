package com.sonograma.service;

import com.sonograma.dto.InvoiceItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VinylFutureSearchServiceTest {

    private final VinylFutureSearchService service = new VinylFutureSearchService();

    @Test
    void rejectsAnOlderProductThatReusesTheSameCatalogueCode() {
        InvoiceItem item = item("SDT002", "Das Firmament", "Malware Bizarre");

        assertThat(service.bestCandidate(item, List.of(
            "https://www.deejay.de/Various_Various_Artist_%232_SDT002_Vinyl__245802"
        ))).isEmpty();
    }

    @Test
    void selectsTheCandidateWhoseCodeArtistAndTitleAgree() {
        InvoiceItem item = item("DST001", "DUST", "The Spirit");

        assertThat(service.bestCandidate(item, List.of(
            "https://www.deejay.de/Furz_Ali_Demir_Reykjavik_4am_DST001_Vinyl__981655",
            "https://www.deejay.de/DUST_The_Spirit_DST001_Vinyl__1266377"
        ))).contains("https://www.deejay.de/DUST_The_Spirit_DST001_Vinyl__1266377");
    }

    private InvoiceItem item(String code, String artist, String title) {
        return new InvoiceItem(code, artist, title, "12inch", BigDecimal.TEN, 1, null);
    }
}
