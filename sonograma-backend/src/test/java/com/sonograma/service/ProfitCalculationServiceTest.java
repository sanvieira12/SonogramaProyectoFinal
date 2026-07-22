package com.sonograma.service;

import com.sonograma.entity.DetalleVenta;
import com.sonograma.entity.Disco;
import com.sonograma.entity.Venta;
import com.sonograma.enums.EstadoVenta;
import com.sonograma.repository.VentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfitCalculationServiceTest {

    private VentaRepository ventaRepository;
    private ProfitCalculationService service;

    @BeforeEach
    void setUp() {
        ventaRepository = mock(VentaRepository.class);
        service = new ProfitCalculationService(ventaRepository);
    }

    @Test
    void calculaGananciaPositivaParaUnItem() {
        ProfitItemResult result = service.netProfitForSoldItem(detail("1000", "400", 1));

        assertThat(result.actualSaleAmount()).isEqualByComparingTo("1000.00");
        assertThat(result.acquisitionCost()).isEqualByComparingTo("400.000000");
        assertThat(result.netProfit()).isEqualByComparingTo("600.00");
        assertThat(result.status()).isEqualTo(ProfitStatus.POSITIVE);
    }

    @Test
    void calculaGananciaNegativa() {
        ProfitItemResult result = service.netProfitForSoldItem(detail("100", "150", 1));

        assertThat(result.netProfit()).isEqualByComparingTo("-50.00");
        assertThat(result.status()).isEqualTo(ProfitStatus.NEGATIVE);
    }

    @Test
    void calculaGananciaCero() {
        ProfitItemResult result = service.netProfitForSoldItem(detail("500", "500", 1));

        assertThat(result.netProfit()).isEqualByComparingTo("0.00");
        assertThat(result.status()).isEqualTo(ProfitStatus.ZERO);
    }

    @Test
    void cantidadMayorQueUnoMultiplicaSoloElCostoUnitarioUnaVez() {
        ProfitItemResult result = service.netProfitForSoldItem(detail("750", "500", 2));

        assertThat(result.actualSaleAmount()).isEqualByComparingTo("1500.00");
        assertThat(result.acquisitionCost()).isEqualByComparingTo("1000.000000");
        assertThat(result.netProfit()).isEqualByComparingTo("500.00");
    }

    @Test
    void sumaItemsIndividualmenteEnUnaVenta() {
        Venta sale = sale("3000", null,
                detail("1000", "400", 1),
                detail("2000", "600", 1));

        ProfitResult result = service.netProfitForSale(sale);

        assertThat(result.netProfit()).isEqualByComparingTo("2000.00");
        assertThat(result.status()).isEqualTo(ProfitStatus.POSITIVE);
        assertThat(result.items()).extracting(ProfitItemResult::netProfit)
                .containsExactly(new BigDecimal("600.00"), new BigDecimal("1400.00"));
    }

    @Test
    void asignaDescuentoProporcionalmenteYConservaElTotal() {
        Venta sale = sale("2700", "10",
                detail("1000", "400", 1),
                detail("2000", "600", 1));

        ProfitResult result = service.netProfitForSale(sale);

        assertThat(result.items()).extracting(ProfitItemResult::actualSaleAmount)
                .containsExactly(new BigDecimal("900.00"), new BigDecimal("1800.00"));
        assertThat(result.items()).extracting(ProfitItemResult::netProfit)
                .containsExactly(new BigDecimal("500.00"), new BigDecimal("1200.00"));
        assertThat(result.items().stream().map(ProfitItemResult::actualSaleAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("2700.00");
    }

    @Test
    void asignaRestoDeRedondeoDeFormaDeterministaAlUltimoItem() {
        Venta sale = sale("1.00", null,
                detail("1", "0", 1),
                detail("1", "0", 1),
                detail("1", "0", 1));

        ProfitResult result = service.netProfitForSale(sale);

        assertThat(result.items()).extracting(ProfitItemResult::actualSaleAmount)
                .containsExactly(new BigDecimal("0.33"), new BigDecimal("0.33"), new BigDecimal("0.34"));
        assertThat(result.netProfit()).isEqualByComparingTo("1.00");
    }

    @Test
    void noUsaElPrecioActualDelCatalogo() {
        Disco disco = Disco.builder()
                .idDisco(8L)
                .precioVenta(new BigDecimal("5000"))
                .costo(new BigDecimal("999"))
                .build();
        DetalleVenta detalle = detail("1200", "400", 1);
        detalle.setDisco(disco);

        ProfitItemResult result = service.netProfitForSoldItem(detalle);

        assertThat(result.netProfit()).isEqualByComparingTo("800.00");
    }

    @Test
    void marcaCostoFaltanteSinConvertirloEnCeroYConservaLosItemsValidos() {
        DetalleVenta missing = detail("1000", null, 1);
        missing.setDisco(Disco.builder().costo(new BigDecimal("999")).build());
        Venta sale = sale("2000", null, missing, detail("1000", "600", 1));

        ProfitResult result = service.netProfitForSale(sale);

        assertThat(result.status()).isEqualTo(ProfitStatus.UNAVAILABLE);
        assertThat(result.affectedItemCount()).isEqualTo(1);
        assertThat(result.netProfit()).isEqualByComparingTo("400.00");
        assertThat(result.items().get(0).netProfit()).isNull();
        assertThat(result.items().get(1).netProfit()).isEqualByComparingTo("400.00");
    }

    @Test
    void recuperaUnCostoHistoricoLegacySoloCuandoLaVentaTieneUnDetalle() {
        DetalleVenta detail = detail("1000", null, 2);
        Venta sale = sale("2000", null, detail);
        sale.setCostoDisco(new BigDecimal("800"));

        ProfitResult result = service.netProfitForSale(sale);

        assertThat(result.status()).isEqualTo(ProfitStatus.POSITIVE);
        assertThat(result.netProfit()).isEqualByComparingTo("1200.00");
        assertThat(result.items().get(0).acquisitionCost()).isEqualByComparingTo("800.000000");
    }

    @Test
    void ventaMultiItemSinSnapshotsNoDistribuyeElCostoAgregado() {
        Venta sale = sale("2000", null,
                detail("1000", null, 1),
                detail("1000", null, 1));
        sale.setCostoDisco(new BigDecimal("1000"));

        ProfitResult result = service.netProfitForSale(sale);

        assertThat(result.status()).isEqualTo(ProfitStatus.UNAVAILABLE);
        assertThat(result.affectedItemCount()).isEqualTo(2);
        assertThat(result.netProfit()).isEqualByComparingTo("0.00");
    }

    @Test
    void excluyeVentasCanceladas() {
        Venta cancelled = sale("1000", null, detail("1000", "400", 1));
        cancelled.setEstado(EstadoVenta.CANCELADA);

        ProfitResult result = service.netProfitForSale(cancelled);

        assertThat(result.items()).isEmpty();
        assertThat(result.status()).isEqualTo(ProfitStatus.ZERO);
    }

    @Test
    void elPeriodoSoloSumaVentasYNoDuplicaPagosDeDeuda() {
        Venta sale = sale("1000", null, detail("1000", "400", 1));
        when(ventaRepository.findAllForProfitPeriod(any(), any())).thenReturn(List.of(sale));

        ProfitResult result = service.netProfitForPeriod(
                LocalDateTime.parse("2026-01-01T00:00:00"),
                LocalDateTime.parse("2026-01-31T23:59:59"));

        assertThat(result.netProfit()).isEqualByComparingTo("600.00");
        assertThat(result.items()).hasSize(1);
        verify(ventaRepository).findAllForProfitPeriod(any(), any());
    }

    private Venta sale(String totalFinal, String discount, DetalleVenta... details) {
        Venta sale = Venta.builder()
                .totalFinal(new BigDecimal(totalFinal))
                .estado(EstadoVenta.COMPLETADA)
                .descuentoPorcentaje(discount == null ? null : new BigDecimal(discount))
                .detalles(List.of(details))
                .build();
        for (DetalleVenta detail : details) detail.setVenta(sale);
        return sale;
    }

    private DetalleVenta detail(String salePrice, String acquisitionCost, int quantity) {
        return DetalleVenta.builder()
                .precioUnitario(new BigDecimal(salePrice))
                .costoAdquisicionUnitario(acquisitionCost == null ? null : new BigDecimal(acquisitionCost))
                .cantidad(quantity)
                .build();
    }
}
