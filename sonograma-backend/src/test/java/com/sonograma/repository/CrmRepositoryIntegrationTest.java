package com.sonograma.repository;

import com.sonograma.entity.*;
import com.sonograma.enums.*;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CrmRepositoryIntegrationTest {

    @Autowired ClienteRepository customers;
    @Autowired DiscoRepository discs;
    @Autowired DiscoQrCopyRepository copies;
    @Autowired VentaRepository sales;
    @Autowired CrmInteresClienteRepository interests;
    @Autowired EntityManager entityManager;

    @Test
    void fetchesCompletedHistoryAndOnlyActionableCrmStock() {
        Cliente customer = new Cliente();
        customer.setNombre("Coleccionista");
        customer.setActivo(true);
        customer = customers.save(customer);

        Disco available = discs.save(disc("Available", EstadoDisco.DISPONIBLE));
        Disco reserved = discs.save(disc("Reserved", EstadoDisco.RESERVADO));
        Disco deleted = discs.save(disc("Deleted", EstadoDisco.DISPONIBLE));
        deleted.setCatalogDeletedAt(LocalDateTime.now());
        discs.save(deleted);
        copies.save(copy(available, EstadoCopiaDisco.DISPONIBLE, 1));
        copies.save(copy(reserved, EstadoCopiaDisco.DISPONIBLE, 1));
        copies.save(copy(deleted, EstadoCopiaDisco.DISPONIBLE, 1));
        copies.save(copy(available, EstadoCopiaDisco.VENDIDO, 2));

        Venta completed = Venta.builder().cliente(customer).fechaVenta(LocalDateTime.now())
                .estado(EstadoVenta.COMPLETADA).build();
        completed.getDetalles().add(DetalleVenta.builder().venta(completed).disco(available)
                .precioUnitario(new BigDecimal("1000")).cantidad(1).manualItem(false).build());
        sales.save(completed);
        Venta cancelled = Venta.builder().cliente(customer).fechaVenta(LocalDateTime.now())
                .estado(EstadoVenta.CANCELADA).build();
        cancelled.getDetalles().add(DetalleVenta.builder().venta(cancelled).disco(available)
                .precioUnitario(new BigDecimal("1000")).cantidad(1).manualItem(false).build());
        sales.save(cancelled);

        assertThat(sales.findCompletedForCrmCustomer(customer.getIdCliente()))
                .extracting(Venta::getIdVenta).containsExactly(completed.getIdVenta());
        assertThat(discs.findAvailableForCrm()).hasSize(1);
        Object[] row = discs.findAvailableForCrm().get(0);
        assertThat(((Disco) row[0]).getIdDisco()).isEqualTo(available.getIdDisco());
        assertThat(((Number) row[1]).longValue()).isEqualTo(1);
    }

    @Test
    void persistsListsAndScopesManualInterests() {
        Cliente first = new Cliente();
        first.setNombre("First");
        first.setActivo(true);
        first = customers.save(first);
        Cliente second = new Cliente();
        second.setNombre("Second");
        second.setActivo(true);
        second = customers.save(second);
        CrmInteresCliente interest = interests.save(CrmInteresCliente.builder().cliente(first)
                .tipo(TipoInteresCrm.SELLO).texto("ECM").activo(true).build());

        assertThat(interests.findByClienteIdClienteAndActivoTrueOrderByFechaCreacionDesc(first.getIdCliente()))
                .extracting(CrmInteresCliente::getTexto).containsExactly("ECM");
        assertThat(interests.findOwned(first.getIdCliente(), interest.getIdInteres())).isPresent();
        assertThat(interests.findOwned(second.getIdCliente(), interest.getIdInteres())).isEmpty();
        assertThat(interests.findAllActiveWithCustomer()).hasSize(1);
    }

    @Test
    void fetchesNestedCrmHistoryInOneStatementForForwardAndReverseProfiles() {
        Cliente customer = new Cliente();
        customer.setNombre("Batch");
        customer.setActivo(true);
        customer = customers.save(customer);
        Disco disc = discs.save(disc("Batch album", EstadoDisco.DISPONIBLE));
        Venta completed = Venta.builder().cliente(customer).fechaVenta(LocalDateTime.now())
                .estado(EstadoVenta.COMPLETADA).build();
        completed.getDetalles().add(DetalleVenta.builder().venta(completed).disco(disc)
                .precioUnitario(new BigDecimal("1000")).cantidad(1).manualItem(false).build());
        sales.save(completed);
        entityManager.flush();
        entityManager.clear();

        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        var forward = sales.findCompletedForCrmCustomer(customer.getIdCliente());
        forward.forEach(sale -> sale.getDetalles().forEach(detail -> detail.getDisco().getArtista()));
        assertThat(statistics.getEntityFetchCount()).isZero();
        assertThat(statistics.getCollectionFetchCount()).isZero();

        entityManager.clear();
        statistics.clear();
        var reverse = sales.findAllCompletedForCrm();
        reverse.forEach(sale -> {
            sale.getCliente().getNombre();
            sale.getDetalles().forEach(detail -> detail.getDisco().getAlbum());
        });
        assertThat(statistics.getEntityFetchCount()).isZero();
        assertThat(statistics.getCollectionFetchCount()).isZero();
    }

    private Disco disc(String album, EstadoDisco state) {
        return Disco.builder().artista("Artist").album(album).estado(state).cantidadCopias(1)
                .pricingMode(PricingMode.AUTO).build();
    }

    private DiscoQrCopy copy(Disco disc, EstadoCopiaDisco state, int number) {
        return DiscoQrCopy.builder().idDisco(disc.getIdDisco()).copyNumber(number)
                .codigoQr(disc.getAlbum() + "-" + number).estado(state).build();
    }
}
