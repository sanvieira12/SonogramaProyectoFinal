package com.sonograma.repository;

import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.entity.DiscogsManualBatch;
import com.sonograma.enums.DiscogsManualBatchStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class DiscogsManualBatchRepositoryIntegrationTest {

    @Autowired
    private DiscogsManualBatchRepository batchRepository;

    @Autowired
    private DiscoQrCopyRepository copyRepository;

    @Test
    void persistsBatchCopyMembershipAndLeavesLegacyCopiesNullable() {
        LocalDateTime now = LocalDateTime.now();
        DiscogsManualBatch batch = batchRepository.saveAndFlush(DiscogsManualBatch.builder()
                .customerCode("jph")
                .normalizedCustomerCode("JPH")
                .status(DiscogsManualBatchStatus.OPEN)
                .startedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build());

        DiscoQrCopy legacy = copyRepository.save(DiscoQrCopy.builder()
                .idDisco(10L).copyNumber(1).codigoQr("legacy-copy").build());
        DiscoQrCopy assigned = copyRepository.save(DiscoQrCopy.builder()
                .idDisco(10L).copyNumber(2).codigoQr("batch-copy")
                .manualDiscogsBatch(batch)
                .precioVenta(new BigDecimal("1450.250000"))
                .condicionFisica("Casi perfecto; pequeña marca junto al borde")
                .build());
        copyRepository.flush();

        assertThat(batchRepository.findByNormalizedCustomerCodeAndStatus("JPH", DiscogsManualBatchStatus.OPEN))
                .contains(batch);
        assertThat(copyRepository.findByManualDiscogsBatchIdOrderByCopyNumber(batch.getId()))
                .extracting(DiscoQrCopy::getId)
                .containsExactly(assigned.getId());

        DiscoQrCopy reloadedLegacy = copyRepository.findById(legacy.getId()).orElseThrow();
        DiscoQrCopy reloadedAssigned = copyRepository.findById(assigned.getId()).orElseThrow();
        assertThat(reloadedLegacy.getManualDiscogsBatch()).isNull();
        assertThat(reloadedLegacy.getPrecioVenta()).isNull();
        assertThat(reloadedLegacy.getCondicionFisica()).isNull();
        assertThat(reloadedAssigned.getPrecioVenta()).isEqualByComparingTo("1450.25");
        assertThat(reloadedAssigned.getCondicionFisica())
                .isEqualTo("Casi perfecto; pequeña marca junto al borde");
    }

    @Test
    void allowsMultipleFinalizedBatchesForTheSameCustomerCode() {
        saveFinalized("JPH");
        saveFinalized("jph");

        assertThat(batchRepository.findAll()).filteredOn(batch ->
                batch.getStatus() == DiscogsManualBatchStatus.FINALIZED).hasSize(2);
    }

    private DiscogsManualBatch saveFinalized(String customerCode) {
        LocalDateTime now = LocalDateTime.now();
        return batchRepository.saveAndFlush(DiscogsManualBatch.builder()
                .customerCode(customerCode)
                .normalizedCustomerCode("JPH")
                .status(DiscogsManualBatchStatus.FINALIZED)
                .startedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .finalizedAt(now)
                .build());
    }
}
