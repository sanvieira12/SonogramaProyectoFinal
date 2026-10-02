package com.sonograma.controller;

import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.entity.DiscogsManualBatch;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.DiscogsManualBatchStatus;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.PricingMode;
import com.sonograma.enums.TipoDisco;
import com.sonograma.repository.DiscoQrCopyRepository;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.service.DiscoService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@WithMockUser
class DiscoSaleSearchIntegrationTest {

    private static final long PHASE_ZERO_BASELINE_BYTES = 559_518L;

    @Autowired private MockMvc mockMvc;
    @Autowired private DiscoService discoService;
    @Autowired private DiscoRepository discoRepository;
    @Autowired private DiscoQrCopyRepository copyRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = Long.toString(System.nanoTime());
    }

    @Test
    void enforcesMinimumAndLimitsWithoutQueryingShortInput() throws Exception {
        createProducts("bounded-token-" + suffix, 55, 1);
        entityManager.flush();

        Statistics statistics = statistics();
        statistics.clear();
        assertThat(discoService.buscarParaVenta(" x ", null)).isEmpty();
        assertThat(statistics.getPrepareStatementCount()).isZero();

        mockMvc.perform(get("/discos/buscar-venta").param("q", "bounded-token-" + suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(20));
        mockMvc.perform(get("/discos/buscar-venta")
                        .param("q", "bounded-token-" + suffix)
                        .param("limit", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7));
        mockMvc.perform(get("/discos/buscar-venta")
                        .param("q", "bounded-token-" + suffix)
                        .param("limit", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(50));
    }

    @Test
    void searchesPreservedFieldsAndUsesPhysicalAvailabilityAsAuthority() {
        Disco rich = saveProduct("ZZ-RICH-" + suffix, "Needle Alpha", "Midnight Beta");
        rich.setGenero("RareGenreGamma");
        rich.setSelloDiscografico("LabelDelta");
        rich.setDescripcion("DescriptionEpsilon");
        rich.setEstado(EstadoDisco.RESERVADO);
        rich.setCondicion(CondicionDisco.CONSIGNACION);
        rich.setCondicionFisica("PhysicalZeta");
        rich.setTipoDisco(TipoDisco.CASSETTE);
        rich.setAnio(1987);
        discoRepository.saveAndFlush(rich);
        saveCopy(rich, 1, EstadoCopiaDisco.DISPONIBLE, null, "1234.50", "VG+");

        Disco aggregateSaysZero = saveProduct("AUTH-ZERO-" + suffix, "Authority Artist", "Available Copy");
        aggregateSaysZero.setCantidadCopias(0);
        aggregateSaysZero.setEstado(EstadoDisco.SIN_STOCK);
        discoRepository.saveAndFlush(aggregateSaysZero);
        saveCopy(aggregateSaysZero, 1, EstadoCopiaDisco.DISPONIBLE, null, null, null);

        Disco aggregateSaysAvailable = saveProduct("AUTH-NONE-" + suffix, "Authority Artist", "No Available Copy");
        aggregateSaysAvailable.setCantidadCopias(99);
        discoRepository.saveAndFlush(aggregateSaysAvailable);
        saveCopy(aggregateSaysAvailable, 1, EstadoCopiaDisco.VENDIDO, null, null, null);
        saveCopy(aggregateSaysAvailable, 2, EstadoCopiaDisco.REMOVED, null, null, null);

        List<String> queries = List.of(
                "needle", "midnight", "raregenre", "labeldelta", "descriptionepsilon",
                "zz-rich", "reservado", "consignacion", "physicalzeta", "cassette", "1987");
        for (String query : queries) {
            assertThat(discoService.buscarParaVenta(query, 20))
                    .extracting(result -> result.idDisco())
                    .contains(rich.getIdDisco());
        }
        assertThat(discoService.buscarParaVenta("authority artist", 20))
                .extracting(result -> result.idDisco())
                .contains(aggregateSaysZero.getIdDisco())
                .doesNotContain(aggregateSaysAvailable.getIdDisco());
        assertThat(discoService.buscarParaVenta("zz-rich-" + suffix, 20))
                .singleElement()
                .satisfies(result -> assertThat(result.requiresExactCopySelection()).isFalse());
    }

    @Test
    void returnsEveryAvailableCopyWithProvenanceAndDeduplicatesSourceMatches() throws Exception {
        DiscogsManualBatch lo = saveBatch("LO");
        DiscogsManualBatch sv3 = saveBatch("SV3");
        Disco product = saveProduct("LO-EXACT-" + suffix, "Lo Artist", "Source Match");
        DiscoQrCopy first = saveCopy(product, 1, EstadoCopiaDisco.DISPONIBLE, lo, "1100", "NM");
        saveCopy(product, 2, EstadoCopiaDisco.VENDIDO, sv3, "950", "VG");
        DiscoQrCopy third = saveCopy(product, 3, EstadoCopiaDisco.DISPONIBLE, sv3, "875", "VG+");
        saveCopy(product, 4, EstadoCopiaDisco.REMOVED, lo, "700", "G+");

        mockMvc.perform(get("/discos/buscar-venta").param("q", " lo "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idDisco").value(product.getIdDisco()))
                .andExpect(jsonPath("$[0].requiresExactCopySelection").value(true))
                .andExpect(jsonPath("$[0].availableCopyCount").value(2))
                .andExpect(jsonPath("$[0].availableCopies.length()").value(2))
                .andExpect(jsonPath("$[0].availableCopies[0].copyId").value(first.getId()))
                .andExpect(jsonPath("$[0].availableCopies[0].sourceCustomerCode").value("LO"))
                .andExpect(jsonPath("$[0].availableCopies[0].normalizedSourceCustomerCode").value("LO"))
                .andExpect(jsonPath("$[0].availableCopies[0].precioVenta").value(1100))
                .andExpect(jsonPath("$[0].availableCopies[0].condicionFisica").value("NM"))
                .andExpect(jsonPath("$[0].availableCopies[1].copyId").value(third.getId()))
                .andExpect(jsonPath("$[0].availableCopies[1].sourceCustomerCode").value("SV3"));

        assertThat(discoService.buscarParaVenta("sv3", 20))
                .singleElement()
                .satisfies(result -> assertThat(result.idDisco()).isEqualTo(product.getIdDisco()));
    }

    @Test
    void excludesTombstonesOrdersDeterministicallyAndHasNoReadSideEffects() {
        Disco exact = saveProduct("needle", "Zulu", "Exact code");
        Disco prefix = saveProduct("PREFIX-" + suffix, "Needlework", "Prefix");
        Disco general = saveProduct("GENERAL-" + suffix, "Alpha", "A needle inside");
        Disco deleted = saveProduct("DELETED-" + suffix, "Needle Deleted", "Hidden");
        deleted.setCatalogDeletedAt(LocalDateTime.now());
        deleted.setCatalogDeletedBy("test");
        discoRepository.saveAndFlush(deleted);
        for (Disco product : List.of(exact, prefix, general, deleted)) {
            saveCopy(product, 1, EstadoCopiaDisco.DISPONIBLE, null, "500", "VG");
        }
        entityManager.flush();

        List<String> before = snapshot(exact.getIdDisco());
        Integer aggregateCount = exact.getCantidadCopias();
        EstadoDisco aggregateState = exact.getEstado();

        List<Long> ids = discoService.buscarParaVenta("needle", 20).stream()
                .map(result -> result.idDisco())
                .toList();
        assertThat(ids).containsExactly(exact.getIdDisco(), prefix.getIdDisco(), general.getIdDisco());

        entityManager.flush();
        entityManager.clear();
        assertThat(snapshot(exact.getIdDisco())).containsExactlyElementsOf(before);
        Disco reloaded = discoRepository.findByIdIncludingCatalogDeleted(exact.getIdDisco()).orElseThrow();
        assertThat(reloaded.getCantidadCopias()).isEqualTo(aggregateCount);
        assertThat(reloaded.getEstado()).isEqualTo(aggregateState);
    }

    @Test
    void executesTwoBoundedQueriesWithoutPerResultFollowUps() {
        createProducts("two-query-" + suffix, 20, 3);
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = statistics();
        statistics.clear();

        assertThat(discoService.buscarParaVenta("two-query-" + suffix, 20)).hasSize(20);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        assertThat(statistics.getEntityUpdateCount()).isZero();
        assertThat(statistics.getEntityInsertCount()).isZero();
        assertThat(statistics.getEntityDeleteCount()).isZero();
    }

    @Test
    void responseIsAtLeastNinetyPercentSmallerThanPhaseZeroBaseline() throws Exception {
        String token = "payload-token-" + suffix;
        createProducts(token, 20, 2);
        entityManager.flush();

        MvcResult result = mockMvc.perform(get("/discos/buscar-venta").param("q", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(20))
                .andReturn();

        long bytes = result.getResponse().getContentAsByteArray().length;
        assertThat(bytes).isLessThan(PHASE_ZERO_BASELINE_BYTES / 10);
        System.out.printf("PHASE3_SALE_SEARCH_BYTES=%d BASELINE_BYTES=%d REDUCTION=%.2f%%%n",
                bytes, PHASE_ZERO_BASELINE_BYTES,
                100.0 - (100.0 * bytes / PHASE_ZERO_BASELINE_BYTES));
    }

    private void createProducts(String token, int productCount, int copiesPerProduct) {
        for (int i = 0; i < productCount; i++) {
            Disco product = saveProduct(String.format("%s-%03d", token, i), "Artist " + i, "Album " + i);
            product.setDescripcion(token + " " + "large-description-".repeat(500));
            discoRepository.saveAndFlush(product);
            for (int copy = 1; copy <= copiesPerProduct; copy++) {
                saveCopy(product, copy, EstadoCopiaDisco.DISPONIBLE, null, "999.99", "VG+");
            }
        }
    }

    private Disco saveProduct(String code, String artist, String album) {
        return discoRepository.saveAndFlush(Disco.builder()
                .codigoInterno(code)
                .codigoQr("aggregate-" + code)
                .artista(artist)
                .album(album)
                .estado(EstadoDisco.DISPONIBLE)
                .cantidadCopias(37)
                .precioVenta(new BigDecimal("1500"))
                .pricingMode(PricingMode.AUTO)
                .build());
    }

    private DiscogsManualBatch saveBatch(String source) {
        DiscogsManualBatch batch = DiscogsManualBatch.builder()
                .customerCode(source)
                .normalizedCustomerCode(source)
                .status(DiscogsManualBatchStatus.OPEN)
                .build();
        entityManager.persist(batch);
        entityManager.flush();
        return batch;
    }

    private DiscoQrCopy saveCopy(
            Disco product,
            int copyNumber,
            EstadoCopiaDisco state,
            DiscogsManualBatch batch,
            String price,
            String condition) {
        return copyRepository.saveAndFlush(DiscoQrCopy.builder()
                .idDisco(product.getIdDisco())
                .copyNumber(copyNumber)
                .codigoQr("copy-" + product.getIdDisco() + "-" + copyNumber + "-" + suffix)
                .estado(state)
                .manualDiscogsBatch(batch)
                .precioVenta(price == null ? null : new BigDecimal(price))
                .condicionFisica(condition)
                .build());
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private List<String> snapshot(Long productId) {
        return copyRepository.findDetailsByIdDisco(productId).stream()
                .map(copy -> String.join("|",
                        copy.getId().toString(),
                        copy.getCopyNumber().toString(),
                        copy.getCodigoQr(),
                        copy.getEstado().name(),
                        copy.getPrecioVenta() == null
                                ? "null" : copy.getPrecioVenta().stripTrailingZeros().toPlainString(),
                        String.valueOf(copy.getCondicionFisica()),
                        String.valueOf(copy.getManualDiscogsBatch() == null
                                ? null : copy.getManualDiscogsBatch().getId())))
                .toList();
    }
}
