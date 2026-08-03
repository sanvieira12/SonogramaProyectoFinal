package com.sonograma.service;

import com.sonograma.config.DataInitializer;
import com.sonograma.entity.CatalogAudioPreview;
import com.sonograma.entity.Cliente;
import com.sonograma.entity.DetalleVenta;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.entity.PreVenta;
import com.sonograma.entity.Reserva;
import com.sonograma.entity.Venta;
import com.sonograma.enums.AudioPreviewStatus;
import com.sonograma.enums.CanalVenta;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.EstadoPago;
import com.sonograma.enums.EstadoReserva;
import com.sonograma.enums.EstadoVenta;
import com.sonograma.enums.PricingMode;
import com.sonograma.enums.TipoEntrega;
import com.sonograma.exception.ConflictoNegocioException;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.ClienteRepository;
import com.sonograma.repository.DetalleVentaRepository;
import com.sonograma.repository.DiscoRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class DiscoPermanentDeletionIntegrationTest {

    @Autowired private DiscoService discoService;
    @Autowired private DiscoRepository discoRepository;
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

    private Venta saveSale(Disco disco) {
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
