package com.sonograma.service.crm;

import com.sonograma.entity.*;
import com.sonograma.enums.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CrmProfileCalculatorTest {

    private final CrmProfileCalculator calculator = new CrmProfileCalculator();

    @Test
    void calculatesQuantityAwarePricesSpendTasteAndRecentProfile() {
        LocalDateTime asOf = LocalDateTime.of(2026, 8, 14, 12, 0);
        Cliente customer = customer(10L);
        Disco aphex = disc(1L, "Aphex Twin", "Selected Ambient Works", "Electronic, IDM", "Ambient", "Warp", 1994, "2 x Vinyl, LP", CondicionDisco.USADO);
        Disco autechre = disc(2L, "Autechre", "Amber", "Electronic, Ambient", "IDM", "Warp", 1998, "LP", CondicionDisco.NUEVO);

        Venta oldSale = sale(100L, customer, asOf.minusMonths(14), EstadoVenta.COMPLETADA, new BigDecimal("10"));
        addDetail(oldSale, 1000L, aphex, new BigDecimal("1000"), 2, false);
        Venta recentSale = sale(101L, customer, asOf.minusMonths(2), EstadoVenta.COMPLETADA, BigDecimal.ZERO);
        addDetail(recentSale, 1001L, autechre, new BigDecimal("3000"), 1, false);
        Venta pending = sale(102L, customer, asOf.minusDays(2), EstadoVenta.PENDIENTE, BigDecimal.ZERO);
        addDetail(pending, 1002L, aphex, new BigDecimal("9000"), 1, false);

        CrmProfileCalculator.CustomerProfile profile = calculator.calculate(customer,
                List.of(oldSale, recentSale, pending), asOf);

        assertThat(profile.metrics().transactionCount()).isEqualTo(2);
        assertThat(profile.metrics().recordCount()).isEqualTo(3);
        assertThat(profile.metrics().totalSpend()).isEqualByComparingTo("4800.00");
        assertThat(profile.metrics().averageOrder()).isEqualByComparingTo("2400.00");
        assertThat(profile.metrics().averageUnitPrice()).isEqualByComparingTo("1600.00");
        assertThat(profile.metrics().medianUnitPrice()).isEqualByComparingTo("900.00");
        assertThat(profile.metrics().typicalMinimum()).isEqualByComparingTo("900.00");
        assertThat(profile.metrics().typicalMaximum()).isEqualByComparingTo("1950.00");
        assertThat(profile.metrics().maximumUnitPrice()).isEqualByComparingTo("3000.00");
        assertThat(profile.metrics().transactionsLast12Months()).isEqualTo(1);
        assertThat(profile.historical().artists().get("aphex twin").count()).isEqualTo(2);
        assertThat(profile.historical().genres().get("electronic").count()).isEqualTo(3);
        assertThat(profile.historical().labels().get("warp").count()).isEqualTo(3);
        assertThat(profile.historical().decades()).containsKey("1990 1999");
        assertThat(profile.historical().formats()).containsKeys("2xlp", "lp");
        assertThat(profile.recent().artists()).containsOnlyKeys("autechre");
        assertThat(profile.purchasedDiscIds()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void supportsLegacyAndManualItemsWithoutMetadata() {
        LocalDateTime asOf = LocalDateTime.of(2026, 8, 14, 12, 0);
        Cliente customer = customer(11L);
        Disco legacyDisc = disc(7L, "Jeff Mills", "Waveform", null, null, null, null, null, null);
        Venta legacy = sale(200L, customer, asOf.minusDays(20), EstadoVenta.COMPLETADA, null);
        legacy.setDisco(legacyDisc);
        legacy.setPrecioVenta(new BigDecimal("2500"));

        Venta manual = sale(201L, customer, asOf.minusDays(5), EstadoVenta.COMPLETADA, BigDecimal.ZERO);
        DetalleVenta detail = DetalleVenta.builder().idDetalle(22L).venta(manual).precioUnitario(new BigDecimal("1800"))
                .cantidad(1).manualItem(true).artistaSnap("Underground Resistance").albumSnap("Manual item").build();
        manual.getDetalles().add(detail);

        CrmProfileCalculator.CustomerProfile profile = calculator.calculate(customer, List.of(legacy, manual), asOf);

        assertThat(profile.metrics().recordCount()).isEqualTo(2);
        assertThat(profile.metrics().totalSpend()).isEqualByComparingTo("4300.00");
        assertThat(profile.historical().artists()).containsKeys("jeff mills", "underground resistance");
        assertThat(profile.historical().genres()).isEmpty();
        assertThat(profile.lines()).anyMatch(CrmProfileCalculator.PurchaseLine::manualItem);
    }

    @Test
    void includesCancelledLegacyHistoryButStillIgnoresPendingSales() {
        LocalDateTime asOf = LocalDateTime.of(2026, 8, 14, 12, 0);
        Cliente customer = customer(13L);
        Venta cancelled = sale(400L, customer, asOf.minusDays(10), EstadoVenta.CANCELADA, BigDecimal.ZERO);
        cancelled.getDetalles().add(manualDetail(cancelled, 4000L, "Signal Priest", "The Marginal EP", "1429"));
        cancelled.getDetalles().add(manualDetail(cancelled, 4002L, "Signal Priest", "The Marginal EP", "1429"));
        Disco pendingDisc = disc(9L, "Signal Priest", "The Marginal EP", "Techno", null,
                "NORD", 2022, "12\"", CondicionDisco.NUEVO);
        Venta pending = sale(401L, customer, asOf.minusDays(2), EstadoVenta.PENDIENTE, BigDecimal.ZERO);
        addDetail(pending, 4001L, pendingDisc, new BigDecimal("9999"), 1, false);

        CrmProfileCalculator.CustomerProfile profile = calculator.calculate(customer,
                List.of(cancelled, pending), asOf);

        assertThat(profile.metrics().transactionCount()).isEqualTo(1);
        assertThat(profile.metrics().recordCount()).isEqualTo(1);
        assertThat(profile.metrics().totalSpend()).isEqualByComparingTo("1429.00");
        assertThat(profile.historical().artists()).containsOnlyKeys("signal priest");
        assertThat(profile.lines()).hasSize(1);
    }

    @Test
    void excludesShippingTaxesAndOtherCheckoutCostsFromLegacyFallback() {
        LocalDateTime asOf = LocalDateTime.of(2026, 8, 14, 12, 0);
        Cliente customer = customer(12L);
        Venta legacy = sale(300L, customer, asOf.minusDays(2), EstadoVenta.COMPLETADA, null);
        legacy.setDisco(disc(8L, "Basic Channel", "BCD", null, null, null, null, null, null));
        legacy.setTotalFinal(new BigDecimal("1450"));
        legacy.setCostoEnvio(new BigDecimal("200"));
        legacy.setMontoImpuesto(new BigDecimal("150"));
        legacy.setOtrosCostos(new BigDecimal("100"));

        CrmProfileCalculator.CustomerProfile profile = calculator.calculate(customer, List.of(legacy), asOf);

        assertThat(profile.metrics().totalSpend()).isEqualByComparingTo("1000.00");
        assertThat(profile.metrics().averageUnitPrice()).isEqualByComparingTo("1000.00");
    }

    private Cliente customer(Long id) {
        Cliente customer = new Cliente();
        customer.setIdCliente(id);
        customer.setNombre("Cliente");
        customer.setActivo(true);
        return customer;
    }

    private Disco disc(Long id, String artist, String album, String genre, String style, String label,
                       Integer year, String format, CondicionDisco condition) {
        return Disco.builder().idDisco(id).artista(artist).album(album).genero(genre).estilo(style)
                .selloDiscografico(label).anio(year).formato(format).condicion(condition)
                .estado(EstadoDisco.DISPONIBLE).cantidadCopias(1).build();
    }

    private Venta sale(Long id, Cliente customer, LocalDateTime date, EstadoVenta state, BigDecimal discount) {
        return Venta.builder().idVenta(id).cliente(customer).fechaVenta(date).estado(state)
                .descuentoPorcentaje(discount).detalles(new ArrayList<>()).build();
    }

    private void addDetail(Venta sale, Long detailId, Disco disc, BigDecimal price, int quantity, boolean manual) {
        sale.getDetalles().add(DetalleVenta.builder().idDetalle(detailId).venta(sale).disco(disc)
                .precioUnitario(price).cantidad(quantity).manualItem(manual)
                .artistaSnap(disc.getArtista()).albumSnap(disc.getAlbum()).build());
    }

    private DetalleVenta manualDetail(Venta sale, Long id, String artist, String album, String price) {
        return DetalleVenta.builder().idDetalle(id).venta(sale).precioUnitario(new BigDecimal(price))
                .cantidad(1).manualItem(true).artistaSnap(artist).albumSnap(album).build();
    }
}
