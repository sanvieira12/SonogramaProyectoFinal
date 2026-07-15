package com.sonograma.service;

import com.sonograma.entity.Disco;
import com.sonograma.entity.PreVenta;
import com.sonograma.repository.PreVentaRepository;
import com.sonograma.repository.DiscoRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PreVentaCodeMatcherTest {
    @Test void normalizaSoloCaseBordesYEspaciosRepetidos() {
        assertThat(PreVentaCodeMatcher.normalize("  AbC-12   / X ")).isEqualTo("abc-12 / x");
        assertThat(PreVentaCodeMatcher.normalize("   ")).isNull();
    }

    @Test void vinculaPendienteSinCobrarla() {
        PreVentaRepository repo = mock(PreVentaRepository.class);
        DiscoRepository discos = mock(DiscoRepository.class);
        PreVenta pending = PreVenta.builder().estado("PENDIENTE").build();
        when(repo.findByCodigoDiscoNormalizadoAndDiscoIsNullAndEstadoNot("vf-99", "PAGADA")).thenReturn(List.of(pending));
        Disco disco = Disco.builder().idDisco(9L).codigoInterno(" VF-99 ").build();
        when(discos.findAll()).thenReturn(List.of(disco));
        int linked = new PreVentaCodeMatcher(repo, discos).linkPendingPreSales(disco);
        assertThat(linked).isEqualTo(1); assertThat(pending.getDisco()).isSameAs(disco); assertThat(pending.getEstado()).isEqualTo("PENDIENTE");
        verify(repo).saveAll(List.of(pending));
    }

    @Test void ignoraCodigoVacio() {
        PreVentaRepository repo = mock(PreVentaRepository.class);
        DiscoRepository discos = mock(DiscoRepository.class);
        assertThat(new PreVentaCodeMatcher(repo, discos).linkPendingPreSales(Disco.builder().idDisco(1L).codigoInterno(" ").build())).isZero();
        verifyNoInteractions(repo);
    }

    @Test void noEligeCuandoElCodigoEsAmbiguo() {
        PreVentaRepository repo = mock(PreVentaRepository.class);
        DiscoRepository discos = mock(DiscoRepository.class);
        Disco first = Disco.builder().idDisco(1L).codigoInterno("ABC 1").build();
        Disco second = Disco.builder().idDisco(2L).codigoInterno(" abc   1 ").build();
        when(discos.findAll()).thenReturn(List.of(first, second));
        assertThat(new PreVentaCodeMatcher(repo, discos).linkPendingPreSales(first)).isZero();
        verifyNoInteractions(repo);
    }
}
