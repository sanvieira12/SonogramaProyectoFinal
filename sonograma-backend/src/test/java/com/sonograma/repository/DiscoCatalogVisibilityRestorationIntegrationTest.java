package com.sonograma.repository;

import com.sonograma.entity.Cliente;
import com.sonograma.entity.DetalleVenta;
import com.sonograma.entity.Deuda;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.entity.Venta;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.EstadoPago;
import com.sonograma.enums.EstadoVenta;
import com.sonograma.enums.PricingMode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class DiscoCatalogVisibilityRestorationIntegrationTest {

    @Autowired private DiscoRepository discos;
    @Autowired private DiscoQrCopyRepository copies;
    @Autowired private ClienteRepository clients;
    @Autowired private VentaRepository sales;
    @Autowired private DetalleVentaRepository saleDetails;
    @Autowired private DeudaRepository debts;
    @Autowired private EntityManager entityManager;

    @Test
    void restoringOnlyCatalogVisibilityKeepsSoldInventoryAndHistoryUntouched() {
        Disco restored = discos.saveAndFlush(disc("MXLP4300", "Restored sold", true));
        Disco unrelatedDeleted = discos.saveAndFlush(disc("OTHER-DELETED", "Unrelated deleted", true));
        Disco active = discos.saveAndFlush(disc("ACTIVE", "Active", false));
        DiscoQrCopy soldCopy = copies.save(DiscoQrCopy.builder()
                .idDisco(restored.getIdDisco())
                .copyNumber(1)
                .codigoQr("sold-qr-15791")
                .estado(EstadoCopiaDisco.VENDIDO)
                .build());

        Cliente client = new Cliente();
        client.setNombre("Historical client");
        client.setActivo(true);
        client = clients.save(client);
        Venta sale = sales.save(Venta.builder()
                .cliente(client)
                .fechaVenta(LocalDateTime.of(2026, 8, 31, 15, 0))
                .estado(EstadoVenta.COMPLETADA)
                .estadoPago(EstadoPago.PARCIAL)
                .totalFinal(new BigDecimal("2800.00"))
                .build());
        DetalleVenta detail = saleDetails.save(DetalleVenta.builder()
                .venta(sale)
                .disco(restored)
                .precioUnitario(new BigDecimal("2800.00"))
                .cantidad(1)
                .copyIdsSnapshot(String.valueOf(soldCopy.getId()))
                .build());
        Deuda debt = debts.save(Deuda.builder()
                .venta(sale)
                .cliente(client)
                .montoTotal(new BigDecimal("2800.00"))
                .montoPagado(new BigDecimal("2500.00"))
                .montoPendiente(new BigDecimal("300.00"))
                .estadoPago(EstadoPago.PARCIAL)
                .activa(true)
                .build());
        entityManager.flush();

        assertThat(discos.findAll())
                .extracting(Disco::getCodigoInterno)
                .containsExactly("ACTIVE");

        LocalDateTime effectiveImportDate = LocalDateTime.of(2026, 9, 2, 4, 30, 27);
        int changed = entityManager.createNativeQuery("""
                UPDATE disco
                   SET catalog_deleted_at = NULL,
                       catalog_deleted_by = NULL,
                       fecha_actualizacion = :effectiveImportDate
                 WHERE id_disco = :id
                   AND catalog_deleted_at IS NOT NULL
                """)
                .setParameter("effectiveImportDate", effectiveImportDate)
                .setParameter("id", restored.getIdDisco())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertThat(changed).isOne();
        assertThat(discos.findAll())
                .extracting(Disco::getCodigoInterno)
                .containsExactlyInAnyOrder("ACTIVE", "MXLP4300")
                .doesNotContain("OTHER-DELETED");
        assertThat(discos.findById(restored.getIdDisco())).get().satisfies(product -> {
            assertThat(product.getCondicion()).isEqualTo(CondicionDisco.NUEVO);
            assertThat(product.getEstado()).isEqualTo(EstadoDisco.VENDIDO);
            assertThat(product.getCantidadCopias()).isZero();
            assertThat(product.getFechaActualizacion()).isEqualTo(effectiveImportDate);
            assertThat(product.getCatalogDeletedAt()).isNull();
            assertThat(product.getCatalogDeletedBy()).isNull();
        });
        assertThat(copies.findById(soldCopy.getId())).get().satisfies(copy -> {
            assertThat(copy.getCodigoQr()).isEqualTo("sold-qr-15791");
            assertThat(copy.getEstado()).isEqualTo(EstadoCopiaDisco.VENDIDO);
        });
        assertThat(copies.countByIdDiscoAndEstado(restored.getIdDisco(), EstadoCopiaDisco.DISPONIBLE)).isZero();
        assertThat(saleDetails.findById(detail.getIdDetalle())).get()
                .extracting(item -> item.getVenta().getIdVenta(), item -> item.getDisco().getIdDisco(), DetalleVenta::getCopyIdsSnapshot)
                .containsExactly(sale.getIdVenta(), restored.getIdDisco(), String.valueOf(soldCopy.getId()));
        assertThat(debts.findById(debt.getIdDeuda())).get().satisfies(savedDebt -> {
            assertThat(savedDebt.getVenta().getIdVenta()).isEqualTo(sale.getIdVenta());
            assertThat(savedDebt.getEstadoPago()).isEqualTo(EstadoPago.PARCIAL);
            assertThat(savedDebt.getMontoPendiente()).isEqualByComparingTo("300.00");
            assertThat(savedDebt.getActiva()).isTrue();
        });
        assertThat(discos.findById(unrelatedDeleted.getIdDisco())).isEmpty();
        assertThat(discos.findById(active.getIdDisco())).isPresent();
    }

    private Disco disc(String code, String album, boolean deleted) {
        return Disco.builder()
                .codigoInterno(code)
                .artista("Test Artist")
                .album(album)
                .condicion(CondicionDisco.NUEVO)
                .estado(deleted ? EstadoDisco.VENDIDO : EstadoDisco.DISPONIBLE)
                .cantidadCopias(deleted ? 0 : 1)
                .pricingMode(PricingMode.AUTO)
                .catalogDeletedAt(deleted ? LocalDateTime.of(2026, 9, 1, 22, 52) : null)
                .catalogDeletedBy(deleted ? "admin" : null)
                .build();
    }
}
