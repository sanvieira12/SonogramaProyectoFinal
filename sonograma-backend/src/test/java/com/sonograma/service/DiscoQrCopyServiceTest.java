package com.sonograma.service;

import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.entity.DiscogsManualBatch;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.DisposicionCopiaReason;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.exception.ConflictoNegocioException;
import com.sonograma.repository.DiscoQrCopyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscoQrCopyServiceTest {

    @Mock
    private DiscoQrCopyRepository repository;

    private DiscoQrCopyService service;

    @BeforeEach
    void setUp() {
        service = new DiscoQrCopyService(repository);
    }

    @Test
    void initializesOnlyTheExactNewUsedCopiesFromExplicitCommercialData() {
        Disco disco = Disco.builder().idDisco(42L).condicion(CondicionDisco.USADO).build();
        DiscoQrCopy created = DiscoQrCopy.builder().id(10L).idDisco(42L).build();
        List<DiscoQrCopy> createdCopies = List.of(created);
        when(repository.saveAll(createdCopies)).thenReturn(createdCopies);

        service.initializeCreatedUsedCopyCommercialData(
                disco, createdCopies, new BigDecimal("900"), " vg+ ");

        assertEquals(new BigDecimal("900"), created.getPrecioVenta());
        assertEquals("vg+", created.getCondicionFisica());
        verify(repository).saveAll(createdCopies);
    }

    @Test
    void doesNotApplyUsedCommercialInitializationToNewProducts() {
        Disco disco = Disco.builder().idDisco(42L).condicion(CondicionDisco.NUEVO).build();
        DiscoQrCopy created = DiscoQrCopy.builder().id(10L).idDisco(42L).build();

        service.initializeCreatedUsedCopyCommercialData(
                disco, List.of(created), new BigDecimal("900"), "VG+");

        assertNull(created.getPrecioVenta());
        assertNull(created.getCondicionFisica());
        verify(repository, never()).saveAll(any());
    }

    @Test
    void removedCopyIsTerminalForGenericStatusChanges() {
        Disco disco = Disco.builder().idDisco(42L).build();
        DiscoQrCopy removed = DiscoQrCopy.builder()
                .id(10L)
                .idDisco(42L)
                .estado(EstadoCopiaDisco.REMOVED)
                .build();
        when(repository.findByIdForUpdate(10L)).thenReturn(java.util.Optional.of(removed));

        ConflictoNegocioException availableError = assertThrows(ConflictoNegocioException.class,
                () -> service.changeCopyStatus(disco, 10L, EstadoCopiaDisco.DISPONIBLE));
        ConflictoNegocioException soldError = assertThrows(ConflictoNegocioException.class,
                () -> service.changeCopyStatus(disco, 10L, EstadoCopiaDisco.VENDIDO));

        assertTrue(availableError.getMessage().contains("terminal"));
        assertTrue(soldError.getMessage().contains("terminal"));
        assertEquals(EstadoCopiaDisco.REMOVED, removed.getEstado());
        verify(repository, never()).save(any(DiscoQrCopy.class));
    }

    @Test
    void enrichedReadModelUsesOnlyPersistedCopyAndBatchDataWithoutWrites() {
        DiscogsManualBatch batch = DiscogsManualBatch.builder()
                .id(77L)
                .customerCode("LO")
                .normalizedCustomerCode("LO")
                .build();
        DiscoQrCopy copy = DiscoQrCopy.builder()
                .id(10L)
                .idDisco(42L)
                .copyNumber(2)
                .codigoQr("persisted-qr")
                .estado(EstadoCopiaDisco.VENDIDO)
                .manualDiscogsBatch(batch)
                .precioVenta(new BigDecimal("1100"))
                .condicionFisica("NM")
                .build();
        when(repository.findDetailsByIdDisco(42L)).thenReturn(List.of(copy));

        var result = service.listDetailDtos(42L);

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).id());
        assertEquals(42L, result.get(0).productId());
        assertEquals("LO", result.get(0).sourceCustomerCode());
        assertEquals("LO", result.get(0).normalizedSourceCustomerCode());
        assertEquals(new BigDecimal("1100"), result.get(0).precioVenta());
        assertEquals("NM", result.get(0).condicionFisica());
        assertEquals("VENDIDO", result.get(0).estado());
        verify(repository).findDetailsByIdDisco(42L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void manualAggregateIncrementIsRejectedWithoutAnonymousCopyCreation() {
        Disco disco = Disco.builder()
            .idDisco(42L)
            .cantidadCopias(2)
            .codigoQr("existing-first-code")
            .build();
        DiscogsManualBatch source = DiscogsManualBatch.builder().id(30L).customerCode("TESTSOURCE").build();
        List<DiscoQrCopy> stored = new ArrayList<>(List.of(DiscoQrCopy.builder()
                .id(1L).idDisco(42L).copyNumber(1).codigoQr("existing-first-code")
                .manualDiscogsBatch(source).precioVenta(new java.math.BigDecimal("1200"))
                .condicionFisica("VG+").build()));

        when(repository.findByIdDiscoOrderByCopyNumber(42L)).thenAnswer(invocation -> new ArrayList<>(stored));

        ConflictoNegocioException error = assertThrows(ConflictoNegocioException.class,
                () -> service.synchronize(disco));

        assertTrue(error.getMessage().contains("recepción exacta"));
        assertEquals(1, stored.size());
        verify(repository, never()).save(any(DiscoQrCopy.class));
    }

    @Test
    void manualAggregateDecrementIsRejectedAndPreservesProvenance() {
        Disco disco = Disco.builder().idDisco(7L).cantidadCopias(1).build();
        DiscogsManualBatch source = DiscogsManualBatch.builder().id(31L).customerCode("TESTSOURCE").build();
        List<DiscoQrCopy> stored = new ArrayList<>(List.of(
            DiscoQrCopy.builder().id(1L).idDisco(7L).copyNumber(1).codigoQr("one").build(),
            DiscoQrCopy.builder().id(2L).idDisco(7L).copyNumber(2).codigoQr("two")
                .manualDiscogsBatch(source).precioVenta(new java.math.BigDecimal("1250"))
                .condicionFisica("VG+").build()
        ));
        when(repository.findByIdDiscoOrderByCopyNumber(7L)).thenAnswer(invocation -> new ArrayList<>(stored));
        ConflictoNegocioException error = assertThrows(ConflictoNegocioException.class,
                () -> service.synchronize(disco));

        assertTrue(error.getMessage().contains("copia física exacta"));
        assertEquals(2, stored.size());
        DiscoQrCopy retained = stored.get(1);
        assertEquals(2L, retained.getId());
        assertEquals("two", retained.getCodigoQr());
        assertSame(source, retained.getManualDiscogsBatch());
        assertEquals(new java.math.BigDecimal("1250"), retained.getPrecioVenta());
        assertEquals("VG+", retained.getCondicionFisica());
        verify(repository, never()).deleteAll(anyList());
    }

    @Test
    void genericAggregateDecrementRetainsExcessAvailableCopyAndQr() {
        Disco disco = Disco.builder().idDisco(50L).cantidadCopias(3).codigoQr("qr-1").build();
        List<DiscoQrCopy> stored = new ArrayList<>(List.of(
                DiscoQrCopy.builder().id(1L).idDisco(50L).copyNumber(1).codigoQr("qr-1").build(),
                DiscoQrCopy.builder().id(2L).idDisco(50L).copyNumber(2).codigoQr("qr-2").build(),
                DiscoQrCopy.builder().id(3L).idDisco(50L).copyNumber(3).codigoQr("qr-3").build()));
        when(repository.findByIdDiscoOrderByCopyNumber(50L)).thenAnswer(invocation -> new ArrayList<>(stored));
        when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        DiscoQrCopyService.CopySynchronizationResult result =
                service.synchronizeAvailableCopiesWithResult(disco, 2);

        assertEquals(3, result.copies().size());
        assertEquals(2, disco.getCantidadCopias());
        assertEquals("qr-1", disco.getCodigoQr());
        assertEquals(EstadoCopiaDisco.REMOVED, stored.get(2).getEstado());
        assertEquals("qr-3", stored.get(2).getCodigoQr());
        assertEquals(DisposicionCopiaReason.REMOVED_FROM_INVENTORY, stored.get(2).getDispositionReason());
        assertEquals("Ajuste de cantidad disponible", stored.get(2).getDispositionNote());
        assertEquals("system:aggregate-quantity", stored.get(2).getDisposedBy());
        assertNotNull(stored.get(2).getDisposedAt());
        verify(repository, never()).deleteAll(anyList());
    }

    @Test
    void restoreCopiesForDebtOnlyRestoresExactSoldCopies() {
        Disco disco = Disco.builder().idDisco(8L).build();
        List<DiscoQrCopy> copies = List.of(
            DiscoQrCopy.builder().id(11L).idDisco(8L).estado(EstadoCopiaDisco.VENDIDO).build(),
            DiscoQrCopy.builder().id(12L).idDisco(8L).estado(EstadoCopiaDisco.VENDIDO).build()
        );
        when(repository.findAllByIdForUpdate(List.of(11L, 12L))).thenReturn(copies);

        service.restoreCopiesForDebt(disco, "11,12");

        assertTrue(copies.stream().allMatch(copy -> copy.getEstado() == EstadoCopiaDisco.DISPONIBLE));
        verify(repository).saveAll(copies);
    }

    @Test
    void restoreCopiesForDebtRejectsCopiesInAnotherState() {
        Disco disco = Disco.builder().idDisco(8L).build();
        DiscoQrCopy copy = DiscoQrCopy.builder().id(11L).idDisco(8L)
            .estado(EstadoCopiaDisco.DISPONIBLE).build();
        when(repository.findAllByIdForUpdate(List.of(11L))).thenReturn(List.of(copy));

        assertThrows(ConflictoNegocioException.class,
            () -> service.restoreCopiesForDebt(disco, "11"));
        verify(repository, never()).saveAll(any());
    }

    @Test
    void restoreCopiesForDebtRejectsMissingExactIdentityForPhysicalInventory() {
        Disco disco = Disco.builder().idDisco(8L).build();

        ConflictoNegocioException error = assertThrows(ConflictoNegocioException.class,
                () -> service.restoreCopiesForDebt(disco, null));

        assertTrue(error.getMessage().contains("no tiene copias identificadas"));
        verifyNoInteractions(repository);
    }

    @Test
    void exactReservationLocksAndSellsOnlyTheRequestedCopy() {
        Disco disco = Disco.builder().idDisco(8L).build();
        DiscoQrCopy selected = DiscoQrCopy.builder().id(12L).idDisco(8L).copyNumber(2)
                .codigoQr("qr-12").estado(EstadoCopiaDisco.DISPONIBLE).build();
        when(repository.findByIdForUpdate(12L)).thenReturn(java.util.Optional.of(selected));
        when(repository.save(selected)).thenReturn(selected);

        List<DiscoQrCopy> result = service.reserveCopies(disco, 1, 12L, "qr-12");

        assertEquals(List.of(selected), result);
        assertEquals(EstadoCopiaDisco.VENDIDO, selected.getEstado());
        verify(repository).findByIdForUpdate(12L);
        verify(repository, never()).findByIdDiscoAndEstadoOrderByCopyNumber(anyLong(), any());
    }

    @Test
    void exactReservationRejectsWrongProductQrAndUnavailableStatesWithoutSubstitution() {
        Disco disco = Disco.builder().idDisco(8L).build();
        DiscoQrCopy wrongProduct = DiscoQrCopy.builder().id(11L).idDisco(9L)
                .codigoQr("qr-11").estado(EstadoCopiaDisco.DISPONIBLE).build();
        DiscoQrCopy wrongQr = DiscoQrCopy.builder().id(12L).idDisco(8L)
                .codigoQr("real-qr").estado(EstadoCopiaDisco.DISPONIBLE).build();
        DiscoQrCopy sold = DiscoQrCopy.builder().id(13L).idDisco(8L)
                .codigoQr("sold").estado(EstadoCopiaDisco.VENDIDO).build();
        DiscoQrCopy removed = DiscoQrCopy.builder().id(14L).idDisco(8L)
                .codigoQr("removed").estado(EstadoCopiaDisco.REMOVED).build();
        when(repository.findByIdForUpdate(11L)).thenReturn(java.util.Optional.of(wrongProduct));
        when(repository.findByIdForUpdate(12L)).thenReturn(java.util.Optional.of(wrongQr));
        when(repository.findByIdForUpdate(13L)).thenReturn(java.util.Optional.of(sold));
        when(repository.findByIdForUpdate(14L)).thenReturn(java.util.Optional.of(removed));

        assertThrows(ConflictoNegocioException.class, () -> service.reserveCopies(disco, 1, 11L, "qr-11"));
        assertThrows(ConflictoNegocioException.class, () -> service.reserveCopies(disco, 1, 12L, "stale-qr"));
        assertThrows(ConflictoNegocioException.class, () -> service.reserveCopies(disco, 1, 13L, "sold"));
        assertThrows(ConflictoNegocioException.class, () -> service.reserveCopies(disco, 1, 14L, "removed"));

        verify(repository, never()).save(any(DiscoQrCopy.class));
        verify(repository, never()).findByIdDiscoAndEstadoOrderByCopyNumber(anyLong(), any());
    }

    @Test
    void qrOnlyCompatibilityLocksTheExactQrRow() {
        Disco disco = Disco.builder().idDisco(8L).build();
        DiscoQrCopy selected = DiscoQrCopy.builder().id(15L).idDisco(8L)
                .codigoQr("printed-qr").estado(EstadoCopiaDisco.DISPONIBLE).build();
        when(repository.findByCodigoQrForUpdate("printed-qr")).thenReturn(java.util.Optional.of(selected));
        when(repository.save(selected)).thenReturn(selected);

        assertEquals(List.of(selected), service.reserveCopies(disco, 1, null, "printed-qr"));
        assertEquals(EstadoCopiaDisco.VENDIDO, selected.getEstado());
        verify(repository).findByCodigoQrForUpdate("printed-qr");
    }

    @Test
    void manualInventoryCannotUseProductLevelFallback() {
        Disco disco = Disco.builder().idDisco(8L).condicion(CondicionDisco.USADO).build();
        DiscoQrCopy manual = DiscoQrCopy.builder().id(20L).idDisco(8L)
                .manualDiscogsBatch(DiscogsManualBatch.builder().id(7L).build())
                .estado(EstadoCopiaDisco.DISPONIBLE).build();
        when(repository.findByIdDiscoAndEstadoOrderByCopyNumber(8L, EstadoCopiaDisco.DISPONIBLE))
                .thenReturn(List.of(manual));

        ConflictoNegocioException error = assertThrows(ConflictoNegocioException.class,
                () -> service.reserveCopies(disco, 1, null, null));

        assertTrue(error.getMessage().contains("copia física exacta"));
        verify(repository, never()).save(any(DiscoQrCopy.class));
    }

    @Test
    void ordinaryUsedSingleCopyIsAutomaticallyReservedAsThatExactRow() {
        Disco disco = Disco.builder().idDisco(8L).condicion(CondicionDisco.USADO).build();
        DiscoQrCopy only = DiscoQrCopy.builder().id(21L).idDisco(8L).copyNumber(1)
                .codigoQr("used-only").estado(EstadoCopiaDisco.DISPONIBLE).build();
        when(repository.findByIdDiscoAndEstadoOrderByCopyNumber(8L, EstadoCopiaDisco.DISPONIBLE))
                .thenReturn(List.of(only));
        when(repository.save(only)).thenReturn(only);

        assertEquals(List.of(only), service.reserveCopies(disco, 1, null, null));
        assertEquals(EstadoCopiaDisco.VENDIDO, only.getEstado());
        verify(repository).save(only);
    }

    @Test
    void ordinaryUsedMultipleCopiesRequireExplicitExactSelection() {
        Disco disco = Disco.builder().idDisco(8L).condicion(CondicionDisco.USADO).build();
        List<DiscoQrCopy> copies = List.of(
                DiscoQrCopy.builder().id(21L).idDisco(8L).copyNumber(1)
                        .estado(EstadoCopiaDisco.DISPONIBLE).build(),
                DiscoQrCopy.builder().id(22L).idDisco(8L).copyNumber(2)
                        .estado(EstadoCopiaDisco.DISPONIBLE).build());
        when(repository.findByIdDiscoAndEstadoOrderByCopyNumber(8L, EstadoCopiaDisco.DISPONIBLE))
                .thenReturn(copies);

        ConflictoNegocioException error = assertThrows(ConflictoNegocioException.class,
                () -> service.reserveCopies(disco, 1, null, null));

        assertTrue(error.getMessage().contains("copia física exacta"));
        assertTrue(copies.stream().allMatch(copy -> copy.getEstado() == EstadoCopiaDisco.DISPONIBLE));
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    void productLevelFallbackRemainsAvailableForEquivalentNewInventory() {
        Disco disco = Disco.builder().idDisco(8L).condicion(CondicionDisco.NUEVO).build();
        DiscoQrCopy first = DiscoQrCopy.builder().id(21L).idDisco(8L).copyNumber(1)
                .estado(EstadoCopiaDisco.DISPONIBLE).build();
        DiscoQrCopy second = DiscoQrCopy.builder().id(22L).idDisco(8L).copyNumber(2)
                .estado(EstadoCopiaDisco.DISPONIBLE).build();
        when(repository.findByIdDiscoAndEstadoOrderByCopyNumber(8L, EstadoCopiaDisco.DISPONIBLE))
                .thenReturn(List.of(first, second));
        when(repository.saveAll(List.of(first))).thenReturn(List.of(first));

        assertEquals(List.of(first), service.reserveCopies(disco, 1, null, null));

        assertEquals(EstadoCopiaDisco.VENDIDO, first.getEstado());
        assertEquals(EstadoCopiaDisco.DISPONIBLE, second.getEstado());
    }

    @Test
    void cancellationNeverRevivesRemovedCopy() {
        DiscoQrCopy removed = DiscoQrCopy.builder().id(16L).idDisco(8L)
                .estado(EstadoCopiaDisco.REMOVED).build();
        when(repository.findAllByIdForUpdate(List.of(16L))).thenReturn(List.of(removed));

        assertThrows(ConflictoNegocioException.class, () -> service.restoreCopies("16"));

        assertEquals(EstadoCopiaDisco.REMOVED, removed.getEstado());
        verify(repository, never()).saveAll(any());
    }

    @Test
    void cancellationRejectsSnapshotCopyFromAnotherProduct() {
        Disco disco = Disco.builder().idDisco(8L).build();
        DiscoQrCopy otherProduct = DiscoQrCopy.builder().id(17L).idDisco(9L)
                .estado(EstadoCopiaDisco.VENDIDO).build();
        when(repository.findAllByIdForUpdate(List.of(17L))).thenReturn(List.of(otherProduct));

        assertThrows(ConflictoNegocioException.class, () -> service.restoreCopies(disco, "17"));

        assertEquals(EstadoCopiaDisco.VENDIDO, otherProduct.getEstado());
        verify(repository, never()).saveAll(any());
    }
}
