package com.sonograma.service.crm;

import com.sonograma.dto.crm.CrmDtos;
import com.sonograma.entity.*;
import com.sonograma.enums.*;
import com.sonograma.repository.CrmInteresClienteRepository;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.VentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrmRecommendationServiceTest {

    @Mock DiscoRepository discs;
    @Mock VentaRepository sales;
    @Mock CrmInteresClienteRepository interestsRepository;
    @Mock CrmProfileService profiles;
    @Mock CrmInterestService interests;

    private CrmRecommendationService service;
    private Cliente customer;
    private CrmProfileCalculator.CustomerProfile profile;

    @BeforeEach
    void setUp() {
        service = new CrmRecommendationService(discs, sales, interestsRepository, profiles, interests,
                new CrmRecommendationProperties());
        customer = new Cliente();
        customer.setIdCliente(9L);
        customer.setNombre("Ignacio");
        customer.setActivo(true);
        Disco purchased = disc(1L, "Aphex Twin", "Selected Ambient Works", "Electronic, IDM", "Ambient", "Warp", 1994, new BigDecimal("4000"));
        Venta sale = Venta.builder().idVenta(1L).cliente(customer).fechaVenta(LocalDateTime.now().minusMonths(3))
                .estado(EstadoVenta.COMPLETADA).detalles(new ArrayList<>()).build();
        sale.getDetalles().add(DetalleVenta.builder().idDetalle(1L).venta(sale).disco(purchased)
                .precioUnitario(new BigDecimal("4000")).cantidad(1).manualItem(false)
                .artistaSnap(purchased.getArtista()).albumSnap(purchased.getAlbum()).build());
        profile = new CrmProfileCalculator().calculate(customer, List.of(sale), LocalDateTime.now());
    }

    @Test
    void ranksArtistAndManualMatchesAndRejectsRepeatOrUnavailableCandidates() {
        Disco repeat = disc(1L, "Aphex Twin", "Selected Ambient Works", "Electronic", "Ambient", "Warp", 1994, new BigDecimal("4000"));
        Disco sameArtist = disc(2L, "Aphex Twin", "Come To Daddy", "Electronic", "IDM", "Warp", 1997, new BigDecimal("4500"));
        Disco manual = disc(3L, "Drexciya", "Neptune's Lair", "Electronic", "Electro", "Tresor", 1999, new BigDecimal("8500"));
        Disco reserved = disc(4L, "Aphex Twin", "Reserved", "Electronic", "IDM", "Warp", 1995, new BigDecimal("4000"));
        reserved.setEstado(EstadoDisco.RESERVADO);
        Disco deleted = disc(5L, "Aphex Twin", "Deleted", "Electronic", "IDM", "Warp", 1995, new BigDecimal("4000"));
        deleted.setCatalogDeletedAt(LocalDateTime.now());
        Disco unavailable = disc(6L, "Aphex Twin", "No copies", "Electronic", "IDM", "Warp", 1995, new BigDecimal("4000"));
        CrmInteresCliente explicit = CrmInteresCliente.builder().idInteres(1L).cliente(customer)
                .tipo(TipoInteresCrm.ARTISTA).texto("Drexciya").activo(true).build();

        when(profiles.profile(9L)).thenReturn(profile);
        when(interests.activeEntities(9L)).thenReturn(List.of(explicit));
        when(discs.findAvailableForCrm()).thenReturn(List.of(
                new Object[]{repeat, 1L}, new Object[]{sameArtist, 2L}, new Object[]{manual, 1L},
                new Object[]{reserved, 1L}, new Object[]{deleted, 1L}, new Object[]{unavailable, 0L}));

        List<CrmDtos.Recomendacion> result = service.recommendations(9L, 20);

        assertThat(result).extracting(CrmDtos.Recomendacion::idDisco).containsExactly(2L, 3L);
        assertThat(result.get(0).razones()).anyMatch(reason -> reason.contains("Aphex Twin"));
        assertThat(result.get(1).razones()).anyMatch(reason -> reason.contains("interés explícito"));
        assertThat(result).allMatch(item -> item.razones().stream().anyMatch(reason -> reason.contains("precio")));
    }

    @Test
    void recentTasteOutweighsOlderTaste() {
        Disco oldDisc = disc(11L, "Old Artist", "Old", "Techno", null, null, 1990, new BigDecimal("3000"));
        Disco recentDisc = disc(12L, "Recent Artist", "Recent", "Ambient", null, null, 2025, new BigDecimal("3000"));
        Venta oldSale = saleWithDisc(11L, oldDisc, LocalDateTime.now().minusMonths(18));
        Venta recentSale = saleWithDisc(12L, recentDisc, LocalDateTime.now().minusMonths(1));
        CrmProfileCalculator.CustomerProfile weighted = new CrmProfileCalculator().calculate(customer,
                List.of(oldSale, recentSale), LocalDateTime.now());
        Disco oldCandidate = disc(21L, "Different", "Old taste", "Techno", null, null, 1991, new BigDecimal("3000"));
        Disco recentCandidate = disc(22L, "Different", "Recent taste", "Ambient", null, null, 2024, new BigDecimal("3000"));

        when(profiles.profile(9L)).thenReturn(weighted);
        when(interests.activeEntities(9L)).thenReturn(List.of());
        when(discs.findAvailableForCrm()).thenReturn(List.of(new Object[]{oldCandidate, 1L}, new Object[]{recentCandidate, 1L}));

        List<CrmDtos.Recomendacion> result = service.recommendations(9L, 20);
        assertThat(result).extracting(CrmDtos.Recomendacion::idDisco).containsExactly(22L, 21L);
    }

    @Test
    void appliesManualCapFormatNormalizationThresholdsAndMeaningfulMatchRule() {
        CrmProfileCalculator.CustomerProfile emptyProfile = new CrmProfileCalculator()
                .calculate(customer, List.of(), LocalDateTime.now());
        Disco combined = disc(31L, "Drexciya", "Neptune's Lair", "Electro", null, "Tresor", 1999, null);
        combined.setFormato("2 x Vinyl, LP");
        Disco partial = disc(32L, "Drexciya", "Uncharted", "Electro", null, "Other", 1999, null);
        Disco compatibilityOnly = disc(33L, "Unrelated", "None", null, null, null, null, new BigDecimal("4000"));
        List<CrmInteresCliente> explicit = List.of(
                interest(TipoInteresCrm.LIBRE, "Drexciya Detroit"),
                interest(TipoInteresCrm.LIBRE, "Tresor Berlin"),
                interest(TipoInteresCrm.FORMATO, "double LP"));

        when(profiles.profile(9L)).thenReturn(emptyProfile);
        when(interests.activeEntities(9L)).thenReturn(explicit);
        when(discs.findAvailableForCrm()).thenReturn(List.of(
                new Object[]{combined, 1L}, new Object[]{partial, 1L}, new Object[]{compatibilityOnly, 1L}));

        List<CrmDtos.Recomendacion> result = service.recommendations(9L, 20);

        assertThat(result).extracting(CrmDtos.Recomendacion::idDisco).containsExactly(31L, 32L);
        assertThat(result.get(0).puntaje()).isEqualByComparingTo("25.00");
        assertThat(result.get(0).nivelAfinidad()).isEqualTo("MEDIA");
        assertThat(result.get(1).puntaje()).isEqualByComparingTo("12.50");
        assertThat(result.get(1).nivelAfinidad()).isEqualTo("BAJA");
    }

    @Test
    void producesHighAffinityAndCapsRequestedLimitAtOneHundred() {
        List<Object[]> candidates = LongStream.rangeClosed(100, 200)
                .mapToObj(id -> new Object[]{disc(id, "Aphex Twin", "Candidate " + id,
                        "Electronic, IDM", "Ambient", "Warp", 1994, new BigDecimal("4000")), 1L})
                .toList();
        when(profiles.profile(9L)).thenReturn(profile);
        when(interests.activeEntities(9L)).thenReturn(List.of());
        when(discs.findAvailableForCrm()).thenReturn(candidates);

        List<CrmDtos.Recomendacion> result = service.recommendations(9L, 500);

        assertThat(result).hasSize(100).allMatch(item -> item.nivelAfinidad().equals("ALTA"));
    }

    @Test
    void reverseRankingIsBatchedAndExcludesTheExactBuyer() {
        Disco candidate = disc(50L, "Drexciya", "Neptune's Lair", "Electro", null, "Tresor", 1999, null);
        Cliente manualCustomer = namedCustomer(20L, "Ana");
        Cliente historyCustomer = namedCustomer(21L, "Bruno");
        Cliente exactBuyer = namedCustomer(22L, "Carla");
        Disco genreDisc = disc(60L, "Other", "Electro history", "Electro", null, null, null, null);
        Venta historySale = saleFor(historyCustomer, 60L, genreDisc);
        Venta exactSale = saleFor(exactBuyer, 61L, candidate);
        CrmInteresCliente manualInterest = CrmInteresCliente.builder().idInteres(10L).cliente(manualCustomer)
                .tipo(TipoInteresCrm.ARTISTA).texto("Drexciya").activo(true).build();

        when(discs.findById(50L)).thenReturn(java.util.Optional.of(candidate));
        when(sales.findAllHistoryForCrm()).thenReturn(List.of(historySale, exactSale));
        when(interestsRepository.findAllActiveWithCustomer()).thenReturn(List.of(manualInterest));
        when(profiles.calculate(any(), any())).thenAnswer(invocation -> new CrmProfileCalculator().calculate(
                invocation.getArgument(0), invocation.getArgument(1), LocalDateTime.now()));

        List<CrmDtos.ClienteAfin> result = service.recommendedCustomers(50L, 20);

        assertThat(result).extracting(item -> item.cliente().getIdCliente()).containsExactly(20L, 21L);
        assertThat(result).extracting(item -> item.cliente().getIdCliente()).doesNotContain(22L);
    }

    private CrmInteresCliente interest(TipoInteresCrm type, String text) {
        return CrmInteresCliente.builder().cliente(customer).tipo(type).texto(text).activo(true).build();
    }

    private Cliente namedCustomer(Long id, String name) {
        Cliente result = new Cliente();
        result.setIdCliente(id);
        result.setNombre(name);
        result.setActivo(true);
        return result;
    }

    private Venta saleFor(Cliente owner, Long id, Disco soldDisc) {
        Venta sale = Venta.builder().idVenta(id).cliente(owner).fechaVenta(LocalDateTime.now().minusMonths(1))
                .estado(EstadoVenta.COMPLETADA).detalles(new ArrayList<>()).build();
        sale.getDetalles().add(DetalleVenta.builder().idDetalle(id).venta(sale).disco(soldDisc)
                .precioUnitario(new BigDecimal("3000")).cantidad(1).manualItem(false)
                .artistaSnap(soldDisc.getArtista()).albumSnap(soldDisc.getAlbum()).build());
        return sale;
    }

    private Venta saleWithDisc(Long id, Disco disc, LocalDateTime date) {
        Venta sale = Venta.builder().idVenta(id).cliente(customer).fechaVenta(date).estado(EstadoVenta.COMPLETADA)
                .detalles(new ArrayList<>()).build();
        sale.getDetalles().add(DetalleVenta.builder().idDetalle(id).venta(sale).disco(disc)
                .precioUnitario(new BigDecimal("3000")).cantidad(1).manualItem(false)
                .artistaSnap(disc.getArtista()).albumSnap(disc.getAlbum()).build());
        return sale;
    }

    private Disco disc(Long id, String artist, String album, String genre, String style, String label,
                       Integer year, BigDecimal price) {
        return Disco.builder().idDisco(id).artista(artist).album(album).genero(genre).estilo(style)
                .selloDiscografico(label).anio(year).formato("LP").condicion(CondicionDisco.NUEVO)
                .precioVenta(price).estado(EstadoDisco.DISPONIBLE).cantidadCopias(1).build();
    }
}
