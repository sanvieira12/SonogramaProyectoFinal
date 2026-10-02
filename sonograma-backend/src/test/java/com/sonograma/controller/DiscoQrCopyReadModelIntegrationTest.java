package com.sonograma.controller;

import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.entity.DiscogsManualBatch;
import com.sonograma.enums.DiscogsManualBatchStatus;
import com.sonograma.enums.DisposicionCopiaReason;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.PricingMode;
import com.sonograma.repository.DiscoQrCopyRepository;
import com.sonograma.repository.DiscoRepository;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@WithMockUser
class DiscoQrCopyReadModelIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DiscoRepository discoRepository;
    @Autowired private DiscoQrCopyRepository copyRepository;
    @Autowired private EntityManager entityManager;

    private Disco disco;
    private DiscoQrCopy availableLo;
    private DiscoQrCopy soldSv3;
    private DiscoQrCopy removedLo;
    private DiscoQrCopy legacy;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        disco = discoRepository.saveAndFlush(Disco.builder()
                .codigoInterno("READ-MODEL-" + suffix)
                .codigoQr("aggregate-qr-" + suffix)
                .artista("Read Model Artist")
                .album("Read Model Album")
                .estado(EstadoDisco.DISPONIBLE)
                .cantidadCopias(1)
                .pricingMode(PricingMode.AUTO)
                .build());

        DiscogsManualBatch lo = saveBatch("LO");
        DiscogsManualBatch sv3 = saveBatch("SV3");
        LocalDateTime removedAt = LocalDateTime.of(2026, 9, 20, 14, 30);

        // Persisted out of display order to verify copy-number/id ordering.
        soldSv3 = saveCopy(2, "qr-sv3-" + suffix, EstadoCopiaDisco.VENDIDO, sv3,
                "925", "VG");
        availableLo = saveCopy(1, "qr-lo-" + suffix, EstadoCopiaDisco.DISPONIBLE, lo,
                "1100", "NM");
        legacy = saveCopy(4, "qr-legacy-" + suffix, EstadoCopiaDisco.DISPONIBLE, null,
                null, null);
        removedLo = copyRepository.saveAndFlush(DiscoQrCopy.builder()
                .idDisco(disco.getIdDisco())
                .copyNumber(3)
                .codigoQr("qr-removed-" + suffix)
                .estado(EstadoCopiaDisco.REMOVED)
                .manualDiscogsBatch(lo)
                .precioVenta(new BigDecimal("800"))
                .condicionFisica("G+")
                .dispositionReason(DisposicionCopiaReason.DAMAGED)
                .dispositionNote("Rayón profundo")
                .disposedAt(removedAt)
                .disposedBy("catalog-admin")
                .build());
        entityManager.flush();
    }

    @Test
    void returnsIndependentRetainedCopiesWithExactProvenanceLifecycleAndOrdering() throws Exception {
        mockMvc.perform(get("/discos/{id}/copias", disco.getIdDisco()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].id").value(availableLo.getId()))
                .andExpect(jsonPath("$[0].productId").value(disco.getIdDisco()))
                .andExpect(jsonPath("$[0].copyNumber").value(1))
                .andExpect(jsonPath("$[0].codigoQr").value(availableLo.getCodigoQr()))
                .andExpect(jsonPath("$[0].estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$[0].precioVenta").value(1100))
                .andExpect(jsonPath("$[0].condicionFisica").value("NM"))
                .andExpect(jsonPath("$[0].sourceCustomerCode").value("LO"))
                .andExpect(jsonPath("$[0].normalizedSourceCustomerCode").value("LO"))
                .andExpect(jsonPath("$[0].manualBatchId").isNumber())
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$[1].id").value(soldSv3.getId()))
                .andExpect(jsonPath("$[1].copyNumber").value(2))
                .andExpect(jsonPath("$[1].estado").value("VENDIDO"))
                .andExpect(jsonPath("$[1].precioVenta").value(925))
                .andExpect(jsonPath("$[1].condicionFisica").value("VG"))
                .andExpect(jsonPath("$[1].sourceCustomerCode").value("SV3"))
                .andExpect(jsonPath("$[1].normalizedSourceCustomerCode").value("SV3"))
                .andExpect(jsonPath("$[2].id").value(removedLo.getId()))
                .andExpect(jsonPath("$[2].estado").value("REMOVED"))
                .andExpect(jsonPath("$[2].dispositionReason").value("DAMAGED"))
                .andExpect(jsonPath("$[2].dispositionNote").value("Rayón profundo"))
                .andExpect(jsonPath("$[2].disposedAt").isNotEmpty())
                .andExpect(jsonPath("$[2].disposedBy").value("catalog-admin"))
                .andExpect(jsonPath("$[2].updatedAt").isNotEmpty())
                .andExpect(jsonPath("$[3].id").value(legacy.getId()))
                .andExpect(jsonPath("$[3].copyNumber").value(4))
                .andExpect(jsonPath("$[3].sourceCustomerCode").doesNotExist())
                .andExpect(jsonPath("$[3].normalizedSourceCustomerCode").doesNotExist())
                .andExpect(jsonPath("$[3].manualBatchId").doesNotExist());
    }

    @Test
    void sameSourceCopiesKeepIndependentPriceConditionQrAndAggregateStock() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        Disco target = discoRepository.saveAndFlush(Disco.builder()
                .codigoInterno("DUPLICATE-LO-" + suffix)
                .codigoQr("duplicate-aggregate-qr-" + suffix)
                .artista("Duplicate LO Artist")
                .album("Duplicate LO Release")
                .estado(EstadoDisco.DISPONIBLE)
                .cantidadCopias(2)
                .pricingMode(PricingMode.AUTO)
                .build());
        DiscogsManualBatch lo = saveBatch("LO");
        DiscoQrCopy first = copyRepository.saveAndFlush(DiscoQrCopy.builder()
                .idDisco(target.getIdDisco())
                .copyNumber(1)
                .codigoQr("duplicate-lo-1-" + suffix)
                .estado(EstadoCopiaDisco.DISPONIBLE)
                .manualDiscogsBatch(lo)
                .precioVenta(new BigDecimal("800"))
                .condicionFisica("NM")
                .build());
        DiscoQrCopy second = copyRepository.saveAndFlush(DiscoQrCopy.builder()
                .idDisco(target.getIdDisco())
                .copyNumber(2)
                .codigoQr("duplicate-lo-2-" + suffix)
                .estado(EstadoCopiaDisco.DISPONIBLE)
                .manualDiscogsBatch(lo)
                .precioVenta(new BigDecimal("790"))
                .condicionFisica("VG+")
                .build());

        mockMvc.perform(get("/discos/{id}/copias", target.getIdDisco()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(first.getId()))
                .andExpect(jsonPath("$[0].codigoQr").value(first.getCodigoQr()))
                .andExpect(jsonPath("$[0].sourceCustomerCode").value("LO"))
                .andExpect(jsonPath("$[0].precioVenta").value(800))
                .andExpect(jsonPath("$[0].condicionFisica").value("NM"))
                .andExpect(jsonPath("$[1].id").value(second.getId()))
                .andExpect(jsonPath("$[1].codigoQr").value(second.getCodigoQr()))
                .andExpect(jsonPath("$[1].sourceCustomerCode").value("LO"))
                .andExpect(jsonPath("$[1].precioVenta").value(790))
                .andExpect(jsonPath("$[1].condicionFisica").value("VG+"));

        entityManager.flush();
        entityManager.clear();
        assertThat(discoRepository.findById(target.getIdDisco()).orElseThrow().getCantidadCopias()).isEqualTo(2);
        assertThat(copyRepository.findDetailsByIdDisco(target.getIdDisco())).hasSize(2);
    }

    @Test
    void repeatedGetDoesNotSynchronizeOrMutateCopiesAggregateOrQrIdentifiers() throws Exception {
        List<String> before = snapshot();
        Integer aggregateCount = disco.getCantidadCopias();
        String aggregateQr = disco.getCodigoQr();

        mockMvc.perform(get("/discos/{id}/copias", disco.getIdDisco())).andExpect(status().isOk());
        mockMvc.perform(get("/discos/{id}/copias", disco.getIdDisco())).andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        assertThat(snapshot()).containsExactlyElementsOf(before);
        Disco reloaded = discoRepository.findById(disco.getIdDisco()).orElseThrow();
        assertThat(reloaded.getCantidadCopias()).isEqualTo(aggregateCount);
        assertThat(reloaded.getCodigoQr()).isEqualTo(aggregateQr);
    }

    @Test
    void missingProductUsesProjectNotFoundResponse() throws Exception {
        mockMvc.perform(get("/discos/{id}/copias", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
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
            int number,
            String qr,
            EstadoCopiaDisco state,
            DiscogsManualBatch batch,
            String price,
            String condition) {
        return copyRepository.saveAndFlush(DiscoQrCopy.builder()
                .idDisco(disco.getIdDisco())
                .copyNumber(number)
                .codigoQr(qr)
                .estado(state)
                .manualDiscogsBatch(batch)
                .precioVenta(price == null ? null : new BigDecimal(price))
                .condicionFisica(condition)
                .build());
    }

    private List<String> snapshot() {
        return copyRepository.findDetailsByIdDisco(disco.getIdDisco()).stream()
                .map(copy -> String.join("|",
                        String.valueOf(copy.getId()),
                        String.valueOf(copy.getCopyNumber()),
                        copy.getCodigoQr(),
                        copy.getEstado().name(),
                        copy.getPrecioVenta() == null ? "null" : copy.getPrecioVenta().stripTrailingZeros().toPlainString(),
                        String.valueOf(copy.getCondicionFisica()),
                        String.valueOf(copy.getManualDiscogsBatch() == null ? null : copy.getManualDiscogsBatch().getId()),
                        String.valueOf(copy.getDispositionReason()),
                        String.valueOf(copy.getDispositionNote()),
                        String.valueOf(copy.getDisposedAt()),
                        String.valueOf(copy.getDisposedBy()),
                        String.valueOf(copy.getUpdatedAt())))
                .toList();
    }
}
