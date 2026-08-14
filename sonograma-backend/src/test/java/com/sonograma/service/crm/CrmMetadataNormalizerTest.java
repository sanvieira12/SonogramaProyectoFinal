package com.sonograma.service.crm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CrmMetadataNormalizerTest {

    @Test
    void normalizesAccentsSeparatorsFormatsAndDecades() {
        assertThat(CrmMetadataNormalizer.normalize("  Electrónica  ")).isEqualTo("electronica");
        assertThat(CrmMetadataNormalizer.split("Techno, Ambient / Techno"))
                .extracting(CrmMetadataNormalizer.Token::key).containsExactly("techno", "ambient");
        assertThat(CrmMetadataNormalizer.format("2 x Vinyl, LP, Album").orElseThrow().key()).isEqualTo("2xlp");
        assertThat(CrmMetadataNormalizer.meaningfulTerms("Looking for German techno from the 90s"))
                .contains("germany", "techno", "1990", "1999")
                .doesNotContain("looking", "from", "the");
    }
}
