package com.sonograma.service;

import com.sonograma.config.DataInitializer;
import com.sonograma.entity.CatalogAudioPreview;
import com.sonograma.entity.Cliente;
import com.sonograma.entity.DetalleVenta;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.entity.DiscogsManualBatch;
import com.sonograma.entity.PreVenta;
import com.sonograma.entity.Reserva;
import com.sonograma.entity.Venta;
import com.sonograma.dto.DiscoResponseDTO;
import com.sonograma.dto.DiscoRequestDTO;
import com.sonograma.enums.AudioPreviewStatus;
import com.sonograma.enums.CanalVenta;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.EstadoPago;
import com.sonograma.enums.EstadoReserva;
import com.sonograma.enums.EstadoVenta;
import com.sonograma.enums.DiscogsManualBatchStatus;
import com.sonograma.enums.DisposicionCopiaReason;
import com.sonograma.enums.PricingMode;
import com.sonograma.enums.TipoEntrega;
import com.sonograma.enums.TipoDisco;
import com.sonograma.exception.ConflictoNegocioException;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.ClienteRepository;
import com.sonograma.repository.DetalleVentaRepository;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.DiscoQrCopyRepository;
import com.sonograma.repository.VentaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class DiscoPermanentDeletionIntegrationTest {

    @Autowired private DiscoService discoService;
    @Autowired private DiscoQrCopyService discoQrCopyService;
    @Autowired private QRService qrService;
    @Autowired private DiscoRepository discoRepository;
    @Autowired private DiscoQrCopyRepository discoQrCopyRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private VentaRepository ventaRepository;
    @Autowired private DetalleVentaRepository detalleVentaRepository;
    @Autowired private DataInitializer dataInitializer;
    @Autowired private EntityManager entityManager;
    @Autowired private MockMvc mockMvc;

    @BeforeEach
    void cleanCatalogTables() {
        entityManager.createNativeQuery("DELETE FROM discogs_import_row").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM discogs_import_job").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM shipping_order_item").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM shipping_order").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM pedido_item").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM pedido").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM reserva").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM pre_venta").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM catalog_audio_preview").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM disco_qr_copy").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM movimiento_stock").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM detalle_venta").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM deuda").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM venta").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM disco").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM cliente").executeUpdate();
    }

    @Test
    void permanentlyDeletesUnreferencedDiscoAndItsNonHistoricalChildren() {
        Disco disco = saveDisco("DELETE-1");
        entityManager.persist(CatalogAudioPreview.builder()
                .idDisco(disco.getIdDisco())
                .audioUrl("https://audio.test/preview.mp3")
                .source("test")
                .status(AudioPreviewStatus.FOUND)
                .build());
        entityManager.persist(DiscoQrCopy.builder()
                .idDisco(disco.getIdDisco())
                .copyNumber(1)
                .codigoQr("copy-delete-1")
                .estado(EstadoCopiaDisco.DISPONIBLE)
                .build());
        entityManager.flush();

        discoService.eliminarDisco(disco.getIdDisco(), "admin");
        entityManager.clear();

        assertThat(discoRepository.findById(disco.getIdDisco())).isEmpty();
        assertThat(physicalDiscoCount(disco.getIdDisco())).isZero();
        assertThat(count("SELECT COUNT(*) FROM catalog_audio_preview WHERE id_disco = " + disco.getIdDisco())).isZero();
        assertThat(count("SELECT COUNT(*) FROM disco_qr_copy WHERE id_disco = " + disco.getIdDisco())).isZero();
    }

    @Test
    void repeatedReadMappingNeverCreatesCopyWhenAggregateSaysStockExists() {
        Disco disco = saveDisco("READ-SYNC");
        String legacyQr = disco.getCodigoQr();
        assertThat(discoQrCopyRepository.findByIdDiscoOrderByCopyNumber(disco.getIdDisco())).isEmpty();

        discoService.obtenerPorId(disco.getIdDisco());
        discoService.obtenerPorId(disco.getIdDisco());
        entityManager.flush();

        assertThat(discoQrCopyRepository.findByIdDiscoOrderByCopyNumber(disco.getIdDisco()))
                .isEmpty();
        assertThat(discoRepository.findById(disco.getIdDisco())).get()
                .extracting(Disco::getCodigoQr).isEqualTo(legacyQr);
    }

    @Test
    void unreferencedProductDeletionTombstonesAndRetainsManualBatchCopyHistory() {
        DiscogsManualBatch batch = saveManualBatch("PHASE0-DELETE");
        Disco disco = saveDisco("MANUAL-HARD-DELETE");
        DiscoQrCopy copy = discoQrCopyRepository.saveAndFlush(DiscoQrCopy.builder()
                .idDisco(disco.getIdDisco())
                .copyNumber(1)
                .codigoQr("phase0-manual-hard-delete")
                .estado(EstadoCopiaDisco.DISPONIBLE)
                .manualDiscogsBatch(batch)
                .precioVenta(new BigDecimal("1100"))
                .condicionFisica("VG+")
                .build());

        discoService.eliminarDisco(disco.getIdDisco(), "phase0-test");
        entityManager.flush();
        entityManager.clear();

        assertThat(discoRepository.findById(disco.getIdDisco())).isEmpty();
        assertThat(physicalDiscoCount(disco.getIdDisco())).isEqualTo(1);
        assertThat(discoQrCopyRepository.findById(copy.getId())).get().satisfies(retained -> {
            assertThat(retained.getCodigoQr()).isEqualTo("phase0-manual-hard-delete");
            assertThat(retained.getManualDiscogsBatch().getId()).isEqualTo(batch.getId());
            assertThat(retained.getPrecioVenta()).isEqualByComparingTo("1100");
            assertThat(retained.getCondicionFisica()).isEqualTo("VG+");
        });
        assertThat(count("SELECT COUNT(*) FROM discogs_manual_batch WHERE id_discogs_manual_batch = " + batch.getId()))
                .isEqualTo(1);
    }

    @Test
    void historicalSaleReferenceSoftDeletesProductAndRetainsManualBatchCopy() {
        DiscogsManualBatch batch = saveManualBatch("PHASE0-SOFT");
        Disco disco = saveDisco("MANUAL-SOFT-DELETE");
        DiscoQrCopy copy = discoQrCopyRepository.saveAndFlush(DiscoQrCopy.builder()
                .idDisco(disco.getIdDisco())
                .copyNumber(1)
                .codigoQr("phase0-manual-soft-delete")
                .estado(EstadoCopiaDisco.VENDIDO)
                .manualDiscogsBatch(batch)
                .precioVenta(new BigDecimal("1200"))
                .condicionFisica("NM")
                .build());
        saveSale(disco);

        discoService.eliminarDisco(disco.getIdDisco(), "phase0-test");
        entityManager.flush();
        entityManager.clear();

        assertThat(discoRepository.findById(disco.getIdDisco())).isEmpty();
        assertThat(physicalDiscoCount(disco.getIdDisco())).isEqualTo(1);
        assertThat(discoQrCopyRepository.findById(copy.getId())).get().satisfies(retained -> {
            assertThat(retained.getCodigoQr()).isEqualTo("phase0-manual-soft-delete");
            assertThat(retained.getManualDiscogsBatch().getId()).isEqualTo(batch.getId());
        });
    }

    @Test
    void deletingMissingOrAlreadyDeletedDiscoReturnsNotFound() {
        assertThatThrownBy(() -> discoService.eliminarDisco(404L, "admin"))
                .isInstanceOf(RecursoNoEncontradoException.class);

        Disco disco = saveDisco("DELETE-TWICE");
        discoService.eliminarDisco(disco.getIdDisco(), "admin");

        assertThatThrownBy(() -> discoService.eliminarDisco(disco.getIdDisco(), "admin"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void historicalSaleIsPreservedBehindPermanentCatalogTombstone() {
        Disco disco = saveDisco("SOLD-1");
        Venta venta = saveSale(disco);

        discoService.eliminarDisco(disco.getIdDisco(), "admin-user");
        entityManager.flush();
        entityManager.clear();

        assertThat(discoRepository.findById(disco.getIdDisco())).isEmpty();
        assertThat(discoRepository.findAll()).noneMatch(item -> item.getIdDisco().equals(disco.getIdDisco()));
        assertThat(physicalDiscoCount(disco.getIdDisco())).isEqualTo(1);
        assertThat(entityManager.createNativeQuery(
                        "SELECT catalog_deleted_by FROM disco WHERE id_disco = " + disco.getIdDisco())
                .getSingleResult()).isEqualTo("admin-user");
        assertThat(ventaRepository.findById(venta.getIdVenta())).isPresent();
        assertThat(detalleVentaRepository.findByVentaIdVenta(venta.getIdVenta())).singleElement()
                .satisfies(detail -> {
                    assertThat(detail.getArtistaSnap()).isEqualTo("Test Artist");
                    assertThat(detail.getCostoAdquisicionUnitarioUyu()).isEqualByComparingTo("500");
                });
    }

    @Test
    void activeReservationBlocksDeletionWithConflictAndKeepsCatalogRecord() {
        Disco disco = saveDisco("RESERVED-1");
        Reserva reserva = new Reserva();
        reserva.setCliente(saveClient());
        reserva.setDisco(disco);
        reserva.setEstado(EstadoReserva.ACTIVA);
        reserva.setFechaReserva(LocalDateTime.now());
        entityManager.persist(reserva);
        entityManager.flush();

        assertThatThrownBy(() -> discoService.eliminarDisco(disco.getIdDisco(), "admin"))
                .isInstanceOf(ConflictoNegocioException.class)
                .hasMessageContaining("reserva activa");
        assertThat(discoRepository.findById(disco.getIdDisco())).isPresent();
    }

    @Test
    void pendingPresaleBlocksDeletionWithConflictAndKeepsCatalogRecord() {
        Disco disco = saveDisco("PRESALE-1");
        entityManager.persist(PreVenta.builder()
                .cliente(saveClient())
                .disco(disco)
                .fecha(LocalDate.now())
                .cantidad(1)
                .precio(new BigDecimal("1000"))
                .estado("PENDIENTE")
                .artistaSnap(disco.getArtista())
                .albumSnap(disco.getAlbum())
                .build());
        entityManager.flush();

        assertThatThrownBy(() -> discoService.eliminarDisco(disco.getIdDisco(), "admin"))
                .isInstanceOf(ConflictoNegocioException.class)
                .hasMessageContaining("preventa pendiente");
        assertThat(discoRepository.findById(disco.getIdDisco())).isPresent();
    }

    @Test
    void tombstoneIsIgnoredByImportDeduplicationAndAllowsAnExplicitNewRecord() {
        Disco old = saveDisco("REIMPORT-1");
        saveSale(old);
        discoService.eliminarDisco(old.getIdDisco(), "admin");
        entityManager.flush();
        entityManager.clear();

        assertThat(discoRepository.findByCodigoInterno("REIMPORT-1")).isEmpty();

        Disco replacement = saveDisco("REIMPORT-1");
        assertThat(replacement.getIdDisco()).isNotEqualTo(old.getIdDisco());
        assertThat(discoRepository.findByCodigoInterno("REIMPORT-1"))
                .get().extracting(Disco::getIdDisco).isEqualTo(replacement.getIdDisco());
    }

    @Test
    void startupInitializationDoesNotRecreatePermanentlyDeletedCatalogRecord() throws Exception {
        Disco disco = saveDisco("STARTUP-1");
        saveSale(disco);
        discoService.eliminarDisco(disco.getIdDisco(), "admin");
        entityManager.flush();
        entityManager.clear();

        dataInitializer.run();
        entityManager.clear();

        assertThat(discoRepository.findById(disco.getIdDisco())).isEmpty();
        assertThat(discoRepository.findByCodigoInterno("STARTUP-1")).isEmpty();
        assertThat(physicalDiscoCount(disco.getIdDisco())).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERADOR")
    void nonAdminCannotDeleteCatalogRecord() throws Exception {
        Disco disco = saveDisco("AUTH-1");

        mockMvc.perform(delete("/discos/{id}", disco.getIdDisco()))
                .andExpect(status().isForbidden());

        assertThat(discoRepository.findById(disco.getIdDisco())).isPresent();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminDeleteEndpointReturnsNoContentOnlyAfterPersistenceSucceeds() throws Exception {
        Disco disco = saveDisco("HTTP-1");

        mockMvc.perform(delete("/discos/{id}", disco.getIdDisco()))
                .andExpect(status().isNoContent());
        entityManager.clear();

        assertThat(discoRepository.findById(disco.getIdDisco())).isEmpty();
        assertThat(physicalDiscoCount(disco.getIdDisco())).isZero();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deleteEndpointReturnsNotFoundForMissingRecord() throws Exception {
        mockMvc.perform(delete("/discos/{id}", 999999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingOneAvailableCopyKeepsSiblingAndProduct() {
        Disco disco = saveDisco("COPY-2");
        DiscoQrCopy first = saveCopy(disco, 1, EstadoCopiaDisco.DISPONIBLE);
        DiscoQrCopy second = saveCopy(disco, 2, EstadoCopiaDisco.DISPONIBLE);
        disco.setCantidadCopias(2);
        discoRepository.saveAndFlush(disco);

        DiscoResponseDTO result = discoService.eliminarCopia(disco.getIdDisco(), second.getId());
        entityManager.clear();

        assertThat(discoRepository.findById(disco.getIdDisco())).isPresent()
                .get().extracting(Disco::getCantidadCopias).isEqualTo(1);
        assertThat(discoQrCopyRepository.findById(first.getId())).isPresent();
        assertThat(discoQrCopyRepository.findById(second.getId())).isEmpty();
        assertThat(result.getCantidadCopias()).isEqualTo(1);
        assertThat(result.getTotalCopias()).isEqualTo(1);
    }

    @Test
    void deletingAvailableCopyFromMixedInventoryLeavesSoldCopyAndRecalculatesParent() {
        Disco disco = saveDisco("COPY-MIXED");
        DiscoQrCopy available = saveCopy(disco, 1, EstadoCopiaDisco.DISPONIBLE);
        DiscoQrCopy sold = saveCopy(disco, 2, EstadoCopiaDisco.VENDIDO);
        disco.setCantidadCopias(1);
        discoRepository.saveAndFlush(disco);

        discoService.eliminarCopia(disco.getIdDisco(), available.getId());
        entityManager.clear();

        assertThat(discoQrCopyRepository.findById(available.getId())).isEmpty();
        assertThat(discoQrCopyRepository.findById(sold.getId())).get()
                .extracting(DiscoQrCopy::getEstado).isEqualTo(EstadoCopiaDisco.VENDIDO);
        assertThat(discoRepository.findById(disco.getIdDisco())).get().satisfies(remaining -> {
            assertThat(remaining.getCantidadCopias()).isZero();
            assertThat(remaining.getEstado()).isEqualTo(EstadoDisco.VENDIDO);
        });
    }

    @Test
    void deletingOneOfThreeAvailableCopiesLeavesTwo() {
        Disco disco = saveDisco("COPY-3");
        saveCopy(disco, 1, EstadoCopiaDisco.DISPONIBLE);
        saveCopy(disco, 2, EstadoCopiaDisco.DISPONIBLE);
        DiscoQrCopy third = saveCopy(disco, 3, EstadoCopiaDisco.DISPONIBLE);
        disco.setCantidadCopias(3);
        discoRepository.saveAndFlush(disco);

        discoService.eliminarCopia(disco.getIdDisco(), third.getId());

        assertThat(discoQrCopyRepository.findByIdDiscoOrderByCopyNumber(disco.getIdDisco())).hasSize(2);
        assertThat(discoRepository.findById(disco.getIdDisco())).get()
                .extracting(Disco::getCantidadCopias).isEqualTo(2);
    }

    @Test
    void copyFromAnotherProductIsRejectedWithoutMutation() {
        Disco firstProduct = saveDisco("COPY-OWNER-A");
        Disco secondProduct = saveDisco("COPY-OWNER-B");
        DiscoQrCopy copy = saveCopy(secondProduct, 1, EstadoCopiaDisco.DISPONIBLE);

        assertThatThrownBy(() -> discoService.eliminarCopia(firstProduct.getIdDisco(), copy.getId()))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(discoQrCopyRepository.findById(copy.getId())).isPresent();
        assertThat(discoRepository.findById(secondProduct.getIdDisco())).isPresent();
    }

    @Test
    void missingCopyIsRejectedAsNotFound() {
        Disco disco = saveDisco("COPY-MISSING");

        assertThatThrownBy(() -> discoService.eliminarCopia(disco.getIdDisco(), 999999L))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(discoRepository.findById(disco.getIdDisco())).isPresent();
    }

    @Test
    void soldCopyWithHistoricalSaleCannotBeDeleted() {
        Disco disco = saveDisco("COPY-HISTORY");
        DiscoQrCopy sold = saveCopy(disco, 1, EstadoCopiaDisco.VENDIDO);
        saveSale(disco);

        assertThatThrownBy(() -> discoService.eliminarCopia(disco.getIdDisco(), sold.getId()))
                .isInstanceOf(ConflictoNegocioException.class)
                .hasMessageContaining("historial de ventas");
        assertThat(discoQrCopyRepository.findById(sold.getId())).isPresent();
        assertThat(ventaRepository.count()).isEqualTo(1);
    }

    @Test
    void exactSaleSnapshotBlocksOnlyReferencedCopyDeletion() {
        Disco disco = saveDisco("COPY-EXACT-HISTORY");
        DiscoQrCopy referenced = saveCopy(disco, 1, EstadoCopiaDisco.DISPONIBLE);
        DiscoQrCopy sibling = saveCopy(disco, 2, EstadoCopiaDisco.DISPONIBLE);
        disco.setCantidadCopias(2);
        discoRepository.saveAndFlush(disco);
        saveSale(disco, String.valueOf(referenced.getId()));

        assertThatThrownBy(() -> discoService.eliminarCopia(disco.getIdDisco(), referenced.getId()))
                .isInstanceOf(ConflictoNegocioException.class)
                .hasMessageContaining("historial de ventas");

        discoService.eliminarCopia(disco.getIdDisco(), sibling.getId());
        assertThat(discoQrCopyRepository.findById(referenced.getId())).isPresent();
        assertThat(discoQrCopyRepository.findById(sibling.getId())).isEmpty();
    }

    @Test
    void activeReservationBlocksExactCopyDeletionAndRetainedRemoval() {
        DiscogsManualBatch batch = saveManualBatch("COPY-RESERVATION-GUARD");
        Disco disco = saveDisco("COPY-RESERVATION-GUARD");
        DiscoQrCopy deletable = saveCopy(disco, 1, EstadoCopiaDisco.DISPONIBLE);
        DiscoQrCopy retained = saveManualCopyWithNumber(disco, batch, EstadoCopiaDisco.DISPONIBLE, 2);
        Reserva reserva = new Reserva();
        reserva.setCliente(saveClient());
        reserva.setDisco(disco);
        reserva.setEstado(EstadoReserva.ACTIVA);
        reserva.setFechaReserva(LocalDateTime.now());
        entityManager.persist(reserva);
        entityManager.flush();

        assertThatThrownBy(() -> discoService.eliminarCopia(disco.getIdDisco(), deletable.getId()))
                .isInstanceOf(ConflictoNegocioException.class)
                .hasMessageContaining("reserva activa");
        assertThatThrownBy(() -> discoService.retirarCopia(
                disco.getIdDisco(), retained.getId(), DisposicionCopiaReason.OTHER, null, "admin"))
                .isInstanceOf(ConflictoNegocioException.class)
                .hasMessageContaining("reserva activa");
        assertThat(discoQrCopyRepository.findById(deletable.getId())).isPresent();
        assertThat(discoQrCopyRepository.findById(retained.getId())).get()
                .extracting(DiscoQrCopy::getEstado).isEqualTo(EstadoCopiaDisco.DISPONIBLE);
    }

    @Test
    void pendingPresaleBlocksExactCopyDeletionAndRetainedRemoval() {
        DiscogsManualBatch batch = saveManualBatch("COPY-PRESALE-GUARD");
        Disco disco = saveDisco("COPY-PRESALE-GUARD");
        DiscoQrCopy deletable = saveCopy(disco, 1, EstadoCopiaDisco.DISPONIBLE);
        DiscoQrCopy retained = saveManualCopyWithNumber(disco, batch, EstadoCopiaDisco.DISPONIBLE, 2);
        entityManager.persist(PreVenta.builder()
                .cliente(saveClient())
                .disco(disco)
                .fecha(LocalDate.now())
                .cantidad(1)
                .precio(new BigDecimal("1000"))
                .estado("PENDIENTE")
                .artistaSnap(disco.getArtista())
                .albumSnap(disco.getAlbum())
                .build());
        entityManager.flush();

        assertThatThrownBy(() -> discoService.eliminarCopia(disco.getIdDisco(), deletable.getId()))
                .isInstanceOf(ConflictoNegocioException.class)
                .hasMessageContaining("preventa pendiente");
        assertThatThrownBy(() -> discoService.retirarCopia(
                disco.getIdDisco(), retained.getId(), DisposicionCopiaReason.OTHER, null, "admin"))
                .isInstanceOf(ConflictoNegocioException.class)
                .hasMessageContaining("preventa pendiente");
        assertThat(discoQrCopyRepository.findById(deletable.getId())).isPresent();
        assertThat(discoQrCopyRepository.findById(retained.getId())).get()
                .extracting(DiscoQrCopy::getEstado).isEqualTo(EstadoCopiaDisco.DISPONIBLE);
    }

    @Test
    void exactSaleSnapshotBlocksRetainedRemoval() {
        DiscogsManualBatch batch = saveManualBatch("COPY-RETAINED-HISTORY");
        Disco disco = saveDisco("COPY-RETAINED-HISTORY");
        DiscoQrCopy copy = saveManualCopy(disco, batch, EstadoCopiaDisco.DISPONIBLE);
        saveSale(disco, String.valueOf(copy.getId()));

        assertThatThrownBy(() -> discoService.retirarCopia(
                disco.getIdDisco(), copy.getId(), DisposicionCopiaReason.OTHER, null, "admin"))
                .isInstanceOf(ConflictoNegocioException.class)
                .hasMessageContaining("historial de ventas");
        assertThat(discoQrCopyRepository.findById(copy.getId())).get()
                .extracting(DiscoQrCopy::getEstado).isEqualTo(EstadoCopiaDisco.DISPONIBLE);
    }

    @Test
    void importedSoldCopyWithoutCommerceCanBeRemovedSafely() {
        Disco disco = saveDisco("COPY-IMPORTED-SOLD");
        DiscoQrCopy sold = saveCopy(disco, 1, EstadoCopiaDisco.VENDIDO);
        disco.setEstado(EstadoDisco.VENDIDO);
        disco.setCantidadCopias(0);
        discoRepository.saveAndFlush(disco);

        discoService.eliminarCopia(disco.getIdDisco(), sold.getId());
        entityManager.clear();

        assertThat(discoQrCopyRepository.findById(sold.getId())).isEmpty();
        assertThat(discoRepository.findById(disco.getIdDisco())).get().satisfies(remaining -> {
            assertThat(remaining.getCantidadCopias()).isZero();
            assertThat(remaining.getEstado()).isEqualTo(EstadoDisco.SIN_STOCK);
        });
    }

    @Test
    void deletingLastAvailableCopyPreservesProductMetadata() {
        Disco disco = saveDisco("COPY-LAST");
        disco.setArtista("Metadata kept");
        disco.setAlbum("Album kept");
        disco.setDiscogsReleaseId(123456L);
        disco.setDiscogsUrl("https://www.discogs.com/release/123456");
        disco.setPrecioVenta(new BigDecimal("850"));
        discoRepository.saveAndFlush(disco);
        DiscoQrCopy only = saveCopy(disco, 1, EstadoCopiaDisco.DISPONIBLE);

        discoService.eliminarCopia(disco.getIdDisco(), only.getId());
        entityManager.clear();

        assertThat(discoRepository.findById(disco.getIdDisco())).get().satisfies(remaining -> {
            assertThat(remaining.getArtista()).isEqualTo("Metadata kept");
            assertThat(remaining.getAlbum()).isEqualTo("Album kept");
            assertThat(remaining.getDiscogsReleaseId()).isEqualTo(123456L);
            assertThat(remaining.getPrecioVenta()).isEqualByComparingTo("850");
            assertThat(remaining.getCantidadCopias()).isZero();
            assertThat(remaining.getEstado()).isEqualTo(EstadoDisco.SIN_STOCK);
        });
    }

    @Test
    void laterManualEditCanAssignNumericSellingPriceToPreviouslyUndefinedPrice() {
        Disco disco = saveDisco("PRICE-EDIT");
        disco.setPrecioVenta(null);
        discoRepository.saveAndFlush(disco);

        DiscoRequestDTO request = new DiscoRequestDTO();
        request.setArtista(disco.getArtista());
        request.setAlbum(disco.getAlbum());
        request.setCondicion(CondicionDisco.USADO);
        request.setTipoDisco(TipoDisco.VINILO);
        request.setPrecioVenta(new BigDecimal("850"));
        request.setPricingMode(PricingMode.MANUAL);

        discoService.actualizarDisco(disco.getIdDisco(), request);
        entityManager.clear();

        assertThat(discoRepository.findById(disco.getIdDisco())).get().satisfies(updated -> {
            assertThat(updated.getPrecioVenta()).isEqualByComparingTo("850");
            assertThat(updated.getPricingMode()).isEqualTo(PricingMode.MANUAL);
        });
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanDeleteOneCopyThroughCopySpecificEndpoint() throws Exception {
        Disco disco = saveDisco("COPY-HTTP");
        DiscoQrCopy copy = saveCopy(disco, 1, EstadoCopiaDisco.DISPONIBLE);

        mockMvc.perform(delete("/discos/{idDisco}/copias/{idCopia}", disco.getIdDisco(), copy.getId()))
                .andExpect(status().isOk());

        assertThat(discoQrCopyRepository.findById(copy.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERADOR")
    void nonAdminCannotDeleteOneCopyThroughCopySpecificEndpoint() throws Exception {
        Disco disco = saveDisco("COPY-AUTH");
        DiscoQrCopy copy = saveCopy(disco, 1, EstadoCopiaDisco.DISPONIBLE);

        mockMvc.perform(delete("/discos/{idDisco}/copias/{idCopia}", disco.getIdDisco(), copy.getId()))
                .andExpect(status().isForbidden());
        assertThat(discoQrCopyRepository.findById(copy.getId())).isPresent();
    }

    @Test
    void exactRetainedRemovalPreservesManualIdentityAndProvenance() {
        DiscogsManualBatch batch = saveManualBatch("PHASE1-REMOVE");
        Disco disco = saveDisco("PHASE1-REMOVE");
        DiscoQrCopy copy = saveManualCopy(disco, batch, EstadoCopiaDisco.DISPONIBLE);
        LocalDateTime receivedAt = copy.getCreatedAt();

        DiscoResponseDTO result = discoService.retirarCopia(
                disco.getIdDisco(), copy.getId(), DisposicionCopiaReason.DAMAGED, " sleeve split ", "admin-user");
        entityManager.flush();
        entityManager.clear();

        assertThat(discoQrCopyRepository.findById(copy.getId())).get().satisfies(retained -> {
            assertThat(retained.getId()).isEqualTo(copy.getId());
            assertThat(retained.getCopyNumber()).isEqualTo(copy.getCopyNumber());
            assertThat(retained.getCodigoQr()).isEqualTo(copy.getCodigoQr());
            assertThat(retained.getEstado()).isEqualTo(EstadoCopiaDisco.REMOVED);
            assertThat(retained.getManualDiscogsBatch().getId()).isEqualTo(batch.getId());
            assertThat(retained.getPrecioVenta()).isEqualByComparingTo("1250");
            assertThat(retained.getCondicionFisica()).isEqualTo("VG+");
            assertThat(retained.getCreatedAt()).isEqualTo(receivedAt);
            assertThat(retained.getDispositionReason()).isEqualTo(DisposicionCopiaReason.DAMAGED);
            assertThat(retained.getDispositionNote()).isEqualTo("sleeve split");
            assertThat(retained.getDisposedAt()).isNotNull();
            assertThat(retained.getDisposedBy()).isEqualTo("admin-user");
        });
        assertThat(result.getCantidadCopias()).isZero();
        assertThat(result.getTotalCopias()).isEqualTo(1);
        assertThat(discoQrCopyRepository.countByIdDiscoAndEstado(
                disco.getIdDisco(), EstadoCopiaDisco.DISPONIBLE)).isZero();
        assertThat(discoQrCopyService.findByCode(copy.getCodigoQr())).extracting(DiscoQrCopy::getId)
                .isEqualTo(copy.getId());
        assertThat(qrService.obtenerPorQRScaneado(copy.getCodigoQr()).getIdDisco()).isEqualTo(disco.getIdDisco());
        Disco persistedProduct = discoRepository.findById(disco.getIdDisco()).orElseThrow();
        assertThatThrownBy(() -> discoQrCopyService.reserveCopies(
                persistedProduct, 1, copy.getId(), copy.getCodigoQr()))
                .isInstanceOf(com.sonograma.exception.ConflictoNegocioException.class)
                .hasMessageContaining("ya no está disponible");
    }

    @Test
    void soldAlreadyRemovedAndWrongProductRetainedRemovalAreRejected() {
        DiscogsManualBatch batch = saveManualBatch("PHASE1-REJECT");
        Disco disco = saveDisco("PHASE1-REJECT-A");
        Disco other = saveDisco("PHASE1-REJECT-B");
        DiscoQrCopy sold = saveManualCopy(disco, batch, EstadoCopiaDisco.VENDIDO);
        DiscoQrCopy removed = saveManualCopyWithNumber(disco, batch, EstadoCopiaDisco.REMOVED, 2);

        assertThatThrownBy(() -> discoService.retirarCopia(
                disco.getIdDisco(), sold.getId(), DisposicionCopiaReason.OTHER, null, null))
                .isInstanceOf(ConflictoNegocioException.class).hasMessageContaining("vendida");
        assertThatThrownBy(() -> discoService.retirarCopia(
                disco.getIdDisco(), removed.getId(), DisposicionCopiaReason.OTHER, null, null))
                .isInstanceOf(ConflictoNegocioException.class).hasMessageContaining("ya fue retirada");
        assertThatThrownBy(() -> discoService.retirarCopia(
                other.getIdDisco(), sold.getId(), DisposicionCopiaReason.OTHER, null, null))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(discoQrCopyRepository.findById(sold.getId())).get()
                .extracting(DiscoQrCopy::getEstado).isEqualTo(EstadoCopiaDisco.VENDIDO);
    }

    @Test
    void manualCopyDeleteStateAndProductStatusBypassesAreRejected() {
        DiscogsManualBatch batch = saveManualBatch("PHASE1-BYPASS");
        Disco disco = saveDisco("PHASE1-BYPASS");
        DiscoQrCopy copy = saveManualCopy(disco, batch, EstadoCopiaDisco.DISPONIBLE);

        assertThatThrownBy(() -> discoService.actualizarCopias(disco.getIdDisco(), 0))
                .isInstanceOf(ConflictoNegocioException.class).hasMessageContaining("copia física exacta");
        assertThatThrownBy(() -> discoService.actualizarCopias(disco.getIdDisco(), 2))
                .isInstanceOf(ConflictoNegocioException.class).hasMessageContaining("recepción exacta");
        assertThatThrownBy(() -> discoService.eliminarCopia(disco.getIdDisco(), copy.getId()))
                .isInstanceOf(ConflictoNegocioException.class).hasMessageContaining("motivo explícito");
        assertThatThrownBy(() -> discoService.cambiarEstadoCopia(
                disco.getIdDisco(), copy.getId(), EstadoCopiaDisco.VENDIDO))
                .isInstanceOf(ConflictoNegocioException.class).hasMessageContaining("flujo de venta");
        assertThatThrownBy(() -> discoService.cambiarEstadoCopia(
                disco.getIdDisco(), copy.getId(), EstadoCopiaDisco.REMOVED))
                .isInstanceOf(ConflictoNegocioException.class).hasMessageContaining("motivo explícito");
        assertThatThrownBy(() -> discoService.cambiarEstado(disco.getIdDisco(), EstadoDisco.VENDIDO))
                .isInstanceOf(ConflictoNegocioException.class).hasMessageContaining("copias físicas");
        assertThatThrownBy(() -> discoService.cambiarEstado(disco.getIdDisco(), EstadoDisco.SIN_STOCK))
                .isInstanceOf(ConflictoNegocioException.class).hasMessageContaining("copias físicas");
        assertThat(discoQrCopyRepository.findById(copy.getId())).get()
                .extracting(DiscoQrCopy::getEstado).isEqualTo(EstadoCopiaDisco.DISPONIBLE);
    }

    @Test
    @WithMockUser(username = "retirement-admin", roles = "ADMIN")
    void retainedRemovalEndpointRequiresReasonAndPersistsAuthenticatedActor() throws Exception {
        DiscogsManualBatch batch = saveManualBatch("PHASE1-HTTP");
        Disco disco = saveDisco("PHASE1-HTTP");
        DiscoQrCopy copy = saveManualCopy(disco, batch, EstadoCopiaDisco.DISPONIBLE);

        mockMvc.perform(post("/discos/{idDisco}/copias/{idCopia}/retiro", disco.getIdDisco(), copy.getId())
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/discos/{idDisco}/copias/{idCopia}/retiro", disco.getIdDisco(), copy.getId())
                        .contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"REMOVED_FROM_INVENTORY\",\"note\":\"customer request\"}"))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        assertThat(discoQrCopyRepository.findById(copy.getId())).get().satisfies(retained -> {
            assertThat(retained.getEstado()).isEqualTo(EstadoCopiaDisco.REMOVED);
            assertThat(retained.getDispositionReason()).isEqualTo(DisposicionCopiaReason.REMOVED_FROM_INVENTORY);
            assertThat(retained.getDisposedBy()).isEqualTo("retirement-admin");
        });
    }

    private Disco saveDisco(String code) {
        return discoRepository.saveAndFlush(Disco.builder()
                .codigoInterno(code)
                .codigoQr("legacy-" + code + "-" + System.nanoTime())
                .artista("Test Artist")
                .album("Test Album")
                .estado(EstadoDisco.DISPONIBLE)
                .cantidadCopias(1)
                .pricingMode(PricingMode.AUTO)
                .build());
    }

    private Cliente saveClient() {
        Cliente client = new Cliente();
        client.setNombre("Test");
        client.setApellido("Client");
        client.setCedula("CI-" + System.nanoTime());
        client.setActivo(true);
        return clienteRepository.save(client);
    }

    private DiscogsManualBatch saveManualBatch(String source) {
        DiscogsManualBatch batch = DiscogsManualBatch.builder()
                .customerCode(source)
                .normalizedCustomerCode(source)
                .status(DiscogsManualBatchStatus.OPEN)
                .build();
        entityManager.persist(batch);
        entityManager.flush();
        return batch;
    }

    private DiscoQrCopy saveCopy(Disco disco, int number, EstadoCopiaDisco state) {
        return discoQrCopyRepository.saveAndFlush(DiscoQrCopy.builder()
                .idDisco(disco.getIdDisco())
                .copyNumber(number)
                .codigoQr("copy-" + disco.getIdDisco() + "-" + number + "-" + System.nanoTime())
                .estado(state)
                .build());
    }

    private DiscoQrCopy saveManualCopy(Disco disco, DiscogsManualBatch batch, EstadoCopiaDisco state) {
        return saveManualCopyWithNumber(disco, batch, state, 1);
    }

    private DiscoQrCopy saveManualCopyWithNumber(
            Disco disco, DiscogsManualBatch batch, EstadoCopiaDisco state, int number) {
        return discoQrCopyRepository.saveAndFlush(DiscoQrCopy.builder()
                .idDisco(disco.getIdDisco())
                .copyNumber(number)
                .codigoQr("manual-" + disco.getIdDisco() + "-" + number + "-" + System.nanoTime())
                .estado(state)
                .manualDiscogsBatch(batch)
                .precioVenta(new BigDecimal("1250"))
                .condicionFisica("VG+")
                .build());
    }

    private Venta saveSale(Disco disco) {
        return saveSale(disco, null);
    }

    private Venta saveSale(Disco disco, String copyIdsSnapshot) {
        Venta venta = ventaRepository.save(Venta.builder()
                .cliente(saveClient())
                .disco(disco)
                .fechaVenta(LocalDateTime.now())
                .canalVenta(CanalVenta.LOCAL)
                .tipoEntrega(TipoEntrega.RETIRO)
                .estado(EstadoVenta.COMPLETADA)
                .estadoPago(EstadoPago.PAGADO)
                .totalFinal(new BigDecimal("1000"))
                .build());
        detalleVentaRepository.save(DetalleVenta.builder()
                .venta(venta)
                .disco(disco)
                .precioUnitario(new BigDecimal("1000"))
                .cantidad(1)
                .artistaSnap(disco.getArtista())
                .albumSnap(disco.getAlbum())
                .codigoSnap(disco.getCodigoInterno())
                .costoAdquisicionUnitarioUyu(new BigDecimal("500"))
                .copyIdsSnapshot(copyIdsSnapshot)
                .build());
        entityManager.flush();
        return venta;
    }

    private int physicalDiscoCount(Long id) {
        return count("SELECT COUNT(*) FROM disco WHERE id_disco = " + id);
    }

    private int count(String sql) {
        return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).intValue();
    }
}
