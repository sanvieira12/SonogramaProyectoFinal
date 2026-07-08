package com.sonograma.service;

import com.sonograma.entity.*;
import com.sonograma.enums.*;
import com.sonograma.repository.*;
import com.sonograma.service.CatalogCleanupService.CatalogCleanupResult;
import com.sonograma.service.CatalogCleanupService.CleanupScope;
import com.sonograma.service.importacion.DiscogsCoverService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class CatalogCleanupServiceTest {

    private static final Path VINYL_DIR = createTempDir("sonograma-vinylfuture-test");
    private static final Path DISCOGS_DIR = createTempDir("sonograma-discogs-test");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("sonograma.vinylfuture.media-directory", () -> VINYL_DIR.toString());
        registry.add("discogs.covers.directory", () -> DISCOGS_DIR.toString());
    }

    @Autowired private CatalogCleanupService cleanupService;
    @Autowired private VinylFutureAssetService vinylFutureAssetService;
    @Autowired private DiscogsCoverService discogsCoverService;
    @Autowired private VinylFutureImportBatchService importBatchService;
    @Autowired private DiscoRepository discoRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private DeudaRepository deudaRepository;
    @Autowired private VentaRepository ventaRepository;
    @Autowired private DetalleVentaRepository detalleVentaRepository;
    @Autowired private PreVentaRepository preVentaRepository;
    @Autowired private EntityManager entityManager;

    @BeforeEach
    void cleanDatabase() throws Exception {
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
        vinylFutureAssetService.clearStoredAssets();
        discogsCoverService.clearStoredCovers();
        importBatchService.clearAll();
    }

    @Test
    void cleanupDeletesCatalogDataButKeepsClientsDebtsAndSalesHistory() throws Exception {
        Cliente cliente = new Cliente();
        cliente.setNombre("Ana");
        cliente.setApellido("Cliente");
        cliente.setCedula("12345678");
        cliente.setActivo(true);
        cliente = clienteRepository.save(cliente);

        Disco sold = discoRepository.save(Disco.builder()
            .codigoInterno("VF-001")
            .codigoQr("legacy-1")
            .artista("Artist A")
            .album("Album A")
            .procedencia("VINYL_FUTURE")
            .estado(EstadoDisco.DISPONIBLE)
            .cantidadCopias(1)
            .formato("VINILO")
            .pricingMode(PricingMode.AUTO)
            .build());
        Disco active = discoRepository.save(Disco.builder()
            .codigoInterno("DG-001")
            .codigoQr("legacy-2")
            .artista("Artist B")
            .album("Album B")
            .procedencia("DISCOGS")
            .estado(EstadoDisco.DISPONIBLE)
            .cantidadCopias(2)
            .formato("VINILO")
            .pricingMode(PricingMode.AUTO)
            .build());

        Venta venta = ventaRepository.save(Venta.builder()
            .cliente(cliente)
            .disco(sold)
            .fechaVenta(LocalDateTime.now())
            .canalVenta(CanalVenta.LOCAL)
            .tipoEntrega(TipoEntrega.RETIRO)
            .estado(EstadoVenta.COMPLETADA)
            .totalFinal(new BigDecimal("1000"))
            .subtotal(new BigDecimal("1000"))
            .clienteNombreSnapshot("Ana Cliente")
            .estadoPago(EstadoPago.PAGADO)
            .build());
        detalleVentaRepository.save(DetalleVenta.builder()
            .venta(venta)
            .disco(sold)
            .precioUnitario(new BigDecimal("1000"))
            .cantidad(1)
            .artistaSnap("Artist A")
            .albumSnap("Album A")
            .codigoSnap("VF-001")
            .build());
        deudaRepository.save(Deuda.builder()
            .venta(venta)
            .cliente(cliente)
            .numeroFactura("F-001")
            .montoTotal(new BigDecimal("1000"))
            .montoPagado(new BigDecimal("200"))
            .montoPendiente(new BigDecimal("800"))
            .fechaVenta(LocalDate.now())
            .fechaDeuda(LocalDate.now())
            .estadoPago(EstadoPago.PENDIENTE)
            .activa(true)
            .build());

        preVentaRepository.save(PreVenta.builder()
            .cliente(cliente)
            .disco(sold)
            .fecha(LocalDate.now())
            .cantidad(1)
            .precio(new BigDecimal("900"))
            .estado("PENDIENTE")
            .artistaSnap("Artist A")
            .albumSnap("Album A")
            .build());
        entityManager.persist(reserva(cliente, active));
        entityManager.persist(audioPreview(sold.getIdDisco(), "https://audio.test/a.mp3"));
        entityManager.persist(audioPreview(active.getIdDisco(), "https://audio.test/b.mp3"));
        entityManager.persist(qrCopy(sold.getIdDisco(), 1, "qr-sold"));
        entityManager.persist(qrCopy(active.getIdDisco(), 1, "qr-active-1"));
        entityManager.persist(stockMove(sold));
        entityManager.persist(stockMove(active));
        persistPedido(active);
        persistShippingOrder(active);
        persistDiscogsImport(active);
        entityManager.flush();

        Files.createDirectories(VINYL_DIR.resolve("VF-001 - Artist A - Album A"));
        Files.writeString(VINYL_DIR.resolve("VF-001 - Artist A - Album A/cover.jpg"), "cover");
        Files.writeString(DISCOGS_DIR.resolve("999.jpg"), "cover");

        String importId = importBatchService.store("csv", java.util.Map.of(), "zip-root", Files.createTempFile("catalog-cleanup", ".zip"));
        assertThat(importBatchService.find(importId)).isPresent();

        CatalogCleanupResult preview = cleanupService.preview(CleanupScope.ALL_CATALOG);
        assertThat(preview.targetDiscos()).isEqualTo(2);
        assertThat(preview.counts().get("detalleVentaDesvincular")).isEqualTo(1);
        assertThat(preview.counts().get("audioPreviewsBorrar")).isEqualTo(2);

        CatalogCleanupResult result = cleanupService.execute(CleanupScope.ALL_CATALOG);
        entityManager.clear();

        assertThat(result.counts().get("discos")).isEqualTo(2);
        assertThat(discoRepository.count()).isZero();
        assertThat(clienteRepository.count()).isEqualTo(1);
        assertThat(deudaRepository.count()).isEqualTo(1);
        assertThat(ventaRepository.count()).isEqualTo(1);
        assertThat(detalleVentaRepository.findAll()).singleElement().satisfies(detalle -> {
            assertThat(detalle.getDisco()).isNull();
            assertThat(detalle.getArtistaSnap()).isEqualTo("Artist A");
        });
        assertThat(ventaRepository.findAll()).singleElement().satisfies(savedVenta ->
            assertThat(savedVenta.getDisco()).isNull()
        );
        assertThat(preVentaRepository.findAll()).singleElement().satisfies(preVenta ->
            assertThat(preVenta.getDisco()).isNull()
        );
        assertThat(count("SELECT COUNT(*) FROM reserva")).isZero();
        assertThat(count("SELECT COUNT(*) FROM catalog_audio_preview")).isZero();
        assertThat(count("SELECT COUNT(*) FROM disco_qr_copy")).isZero();
        assertThat(count("SELECT COUNT(*) FROM movimiento_stock")).isZero();
        assertThat(count("SELECT COUNT(*) FROM discogs_import_job")).isZero();
        assertThat(count("SELECT COUNT(*) FROM discogs_import_row")).isZero();
        assertThat(count("SELECT COUNT(*) FROM pedido_item WHERE id_disco IS NULL")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM shipping_order_item WHERE id_disco IS NULL")).isEqualTo(1);
        assertThat(Files.exists(VINYL_DIR.resolve("VF-001 - Artist A - Album A/cover.jpg"))).isFalse();
        assertThat(Files.exists(DISCOGS_DIR.resolve("999.jpg"))).isFalse();
        assertThat(importBatchService.find(importId)).isEmpty();

        CatalogCleanupResult secondRun = cleanupService.execute(CleanupScope.ALL_CATALOG);
        assertThat(secondRun.targetDiscos()).isZero();
        assertThat(clienteRepository.count()).isEqualTo(1);
        assertThat(deudaRepository.count()).isEqualTo(1);
        assertThat(ventaRepository.count()).isEqualTo(1);
    }

    private Reserva reserva(Cliente cliente, Disco disco) {
        Reserva reserva = new Reserva();
        reserva.setCliente(cliente);
        reserva.setDisco(disco);
        reserva.setEstado(EstadoReserva.ACTIVA);
        reserva.setFechaReserva(LocalDateTime.now());
        return reserva;
    }

    private CatalogAudioPreview audioPreview(Long idDisco, String url) {
        return CatalogAudioPreview.builder()
            .idDisco(idDisco)
            .trackName("Track")
            .trackPosition("A1")
            .audioUrl(url)
            .source("vinylfuture")
            .status(AudioPreviewStatus.FOUND)
            .build();
    }

    private DiscoQrCopy qrCopy(Long idDisco, int number, String code) {
        return DiscoQrCopy.builder()
            .idDisco(idDisco)
            .copyNumber(number)
            .codigoQr(code)
            .estado(EstadoCopiaDisco.DISPONIBLE)
            .build();
    }

    private MovimientoStock stockMove(Disco disco) {
        return MovimientoStock.builder()
            .disco(disco)
            .tipoMovimiento("INGRESO")
            .cantidad(1)
            .descripcion("seed")
            .build();
    }

    private void persistPedido(Disco disco) {
        Pedido pedido = Pedido.builder()
            .numeroFactura("P-001")
            .proveedor("Vinyl Future")
            .fechaFactura(LocalDate.now())
            .build();
        entityManager.persist(pedido);
        PedidoItem item = PedidoItem.builder()
            .pedido(pedido)
            .codigo(disco.getCodigoInterno())
            .artista(disco.getArtista())
            .titulo(disco.getAlbum())
            .cantidad(1)
            .disco(disco)
            .build();
        entityManager.persist(item);
    }

    private void persistShippingOrder(Disco disco) {
        ShippingOrder order = ShippingOrder.builder()
            .numero("SO-TEST-001")
            .proveedor("Vinyl Future")
            .fechaOrden(LocalDate.now())
            .estado(EstadoShippingOrder.RECIBIDO)
            .build();
        entityManager.persist(order);
        ShippingOrderItem item = ShippingOrderItem.builder()
            .shippingOrder(order)
            .disco(disco)
            .artista(disco.getArtista())
            .album(disco.getAlbum())
            .cantidad(1)
            .build();
        entityManager.persist(item);
    }

    private void persistDiscogsImport(Disco disco) {
        DiscogsImportJob job = DiscogsImportJob.builder()
            .nombreArchivo("discogs.xlsx")
            .nombreHoja("Links")
            .status(DiscogsImportJobStatus.COMPLETED)
            .rows(new java.util.ArrayList<>())
            .build();
        entityManager.persist(job);
        DiscogsImportRow row = DiscogsImportRow.builder()
            .job(job)
            .sourceExcelRowNumber(2)
            .discogsType("release")
            .discogsId(999L)
            .artist(disco.getArtista())
            .title(disco.getAlbum())
            .sourceStatus("DISPONIBLE")
            .status(DiscogsImportRowStatus.IMPORTED)
            .importedCatalogProduct(disco)
            .build();
        entityManager.persist(row);
    }

    private int count(String sql) {
        Number value = (Number) entityManager.createNativeQuery(sql).getSingleResult();
        return value == null ? 0 : value.intValue();
    }

    private static Path createTempDir(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
