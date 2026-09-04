package com.sonograma.repository;

import com.sonograma.entity.DiscogsImportJob;
import com.sonograma.entity.DiscogsImportRow;
import com.sonograma.entity.Disco;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.DiscogsCatalogImportStatus;
import com.sonograma.enums.DiscogsImportJobStatus;
import com.sonograma.enums.DiscogsImportRowStatus;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.TipoDisco;
import com.sonograma.dto.DiscogsCatalogSourceDTO;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.entity.DiscogsManualBatch;
import com.sonograma.enums.DiscogsManualBatchStatus;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.service.DiscoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
class DiscogsCatalogJobFilterRepositoryTest {

    @Autowired
    private DiscogsImportRowRepository rowRepository;

    @Autowired
    private DiscogsImportJobRepository jobRepository;

    @Autowired
    private DiscoRepository discoRepository;

    @Autowired
    private DiscoQrCopyRepository copyRepository;

    @Autowired
    private DiscogsManualBatchRepository manualBatchRepository;

    @Autowired
    private DiscoService discoService;

    @BeforeEach
    void clean() {
        rowRepository.deleteAll();
        jobRepository.deleteAll();
        copyRepository.deleteAll();
        manualBatchRepository.deleteAll();
        discoRepository.deleteAll();
    }

    @Test
    void logicalPinSourceReturns238DistinctProductsIncludingReusedJob22Rows() {
        DiscogsImportJob job23 = jobRepository.save(DiscogsImportJob.builder()
                .nombreArchivo("PIN corregido.xlsx")
                .status(DiscogsImportJobStatus.COMPLETED)
                .build());
        DiscogsImportJob job22 = jobRepository.save(DiscogsImportJob.builder()
                .nombreArchivo("PIN corregido.xlsx")
                .status(DiscogsImportJobStatus.COMPLETED)
                .build());

        List<Disco> jobProducts = new ArrayList<>();
        for (int index = 0; index < 238; index++) {
            jobProducts.add(discoRepository.save(catalogProduct(index)));
        }
        Disco unrelatedProduct = discoRepository.save(catalogProduct(999));

        List<DiscogsImportRow> rows = new ArrayList<>();
        for (int index = 0; index < 238; index++) {
            rows.add(importedRow(job23, jobProducts.get(index), index + 2,
                    index < 18 ? "EXISTING_PRODUCT" : "NEW_PRODUCT"));
        }
        // Job 22 is the historical same-source import: its 18 products must not create duplicate cards.
        for (int index = 0; index < 18; index++) {
            rows.add(importedRow(job22, jobProducts.get(index), index + 2, "EXISTING_PRODUCT"));
        }
        // Three repeated releases: one product card, a receipt row per workbook row.
        rows.add(importedRow(job23, jobProducts.get(0), 300, "NEW_PRODUCT"));
        rows.add(importedRow(job23, jobProducts.get(1), 301, "NEW_PRODUCT"));
        rows.add(importedRow(job23, jobProducts.get(2), 302, "NEW_PRODUCT"));
        rowRepository.saveAll(rows);

        List<Disco> filtered = rowRepository.findDistinctActiveCatalogProductsByJobIds(List.of(
                job22.getIdDiscogsImportJob(), job23.getIdDiscogsImportJob()
        ));

        assertThat(filtered).hasSize(238);
        assertThat(filtered).extracting(Disco::getIdDisco).doesNotHaveDuplicates();
        assertThat(filtered).extracting(Disco::getIdDisco)
                .containsExactlyInAnyOrderElementsOf(jobProducts.stream().map(Disco::getIdDisco).toList())
                .doesNotContain(unrelatedProduct.getIdDisco());
        assertThat(filtered).extracting(Disco::getIdDisco)
                .containsAll(jobProducts.subList(0, 18).stream().map(Disco::getIdDisco).toList());
        assertThat(discoRepository.findAll()).hasSize(239);
    }

    @Test
    void catalogSourcesArePersistedNamesDeduplicatedAndFilterable() {
        DiscogsImportJob pin = jobRepository.save(DiscogsImportJob.builder()
                .nombreArchivo("Discos PIN.xlsx")
                .status(DiscogsImportJobStatus.COMPLETED)
                .build());
        DiscogsImportJob pinReplay = jobRepository.save(DiscogsImportJob.builder()
                .nombreArchivo(" discos pin.XLSX ")
                .status(DiscogsImportJobStatus.COMPLETED)
                .build());
        DiscogsImportJob jph = jobRepository.save(DiscogsImportJob.builder()
                .nombreArchivo("JPH PARA CATALOGO Y WEB.xlsx")
                .status(DiscogsImportJobStatus.COMPLETED)
                .build());
        DiscogsImportJob frank = jobRepository.save(DiscogsImportJob.builder()
                .nombreArchivo("Discos FRANK.xlsx")
                .status(DiscogsImportJobStatus.COMPLETED)
                .build());

        Disco pinProduct = discoRepository.save(catalogProduct(2000));
        Disco jphProduct = discoRepository.save(catalogProduct(2001));
        Disco frankProduct = discoRepository.save(catalogProduct(2002));
        rowRepository.saveAll(List.of(
                importedRow(pin, pinProduct, 2, "NEW_PRODUCT"),
                importedRow(pinReplay, pinProduct, 2, "EXISTING_PRODUCT"),
                importedRow(jph, jphProduct, 2, "NEW_PRODUCT"),
                importedRow(frank, frankProduct, 2, "NEW_PRODUCT")
        ));

        List<DiscogsCatalogSourceDTO> sources = rowRepository.findCatalogSources();

        assertThat(sources).extracting(DiscogsCatalogSourceDTO::label)
                .containsExactlyInAnyOrder(
                        "Discos PIN.xlsx", "JPH PARA CATALOGO Y WEB.xlsx", "Discos FRANK.xlsx");
        assertThat(sources).extracting(DiscogsCatalogSourceDTO::key)
                .doesNotHaveDuplicates();
        assertThat(sources).filteredOn(source -> source.label().equals("Discos PIN.xlsx"))
                .singleElement().extracting(DiscogsCatalogSourceDTO::productos).isEqualTo(1L);
        assertThat(rowRepository.findDistinctActiveCatalogProductsBySource("jph para catalogo y web.xlsx"))
                .extracting(Disco::getIdDisco).containsExactly(jphProduct.getIdDisco());
    }

    @Test
    void manualSourcesCountPhysicalCopiesAndFilterByExactBatchMembership() {
        DiscogsManualBatch first = manualBatchRepository.save(manualBatch("JPH", DiscogsManualBatchStatus.OPEN));
        DiscogsManualBatch second = manualBatchRepository.save(manualBatch("JPH", DiscogsManualBatchStatus.FINALIZED));
        Disco shared = discoRepository.save(catalogProduct(3000));
        Disco onlyInSecond = discoRepository.save(catalogProduct(3001));

        copyRepository.saveAll(List.of(
                copy(shared, first, 1, "first-a", "1000", "VG"),
                copy(shared, first, 2, "first-b", "2000", "EX"),
                copy(shared, second, 3, "second-a", "3000", "NM"),
                copy(onlyInSecond, second, 4, "second-b", "4000", "MINT")
        ));

        List<DiscogsCatalogSourceDTO> sources = manualBatchRepository.findCatalogSources();

        assertThat(sources).extracting(DiscogsCatalogSourceDTO::key)
                .containsExactly("manual:" + second.getId(), "manual:" + first.getId());
        assertThat(sources).extracting(DiscogsCatalogSourceDTO::productos)
                .containsExactly(2L, 2L);
        assertThat(discoService.listarFuentesImportacionDiscogs())
                .filteredOn(source -> source.batchId().equals(first.getId()))
                .singleElement()
                .satisfies(source -> {
                    assertThat(source.label()).isEqualTo("JPH · 2 discos · En curso");
                    assertThat(source.status()).isEqualTo(DiscogsManualBatchStatus.OPEN);
                });
        assertThat(discoService.listarFuentesImportacionDiscogs())
                .filteredOn(source -> source.batchId().equals(second.getId()))
                .singleElement()
                .extracting(DiscogsCatalogSourceDTO::label)
                .isEqualTo("JPH · 2 discos · Finalizada");
        assertThat(discoService.obtenerTodos(null, "manual:" + first.getId()))
                .extracting(dto -> dto.getIdDisco())
                .containsExactly(shared.getIdDisco());
        assertThat(discoService.obtenerTodos(null, "manual:" + first.getId()))
                .singleElement()
                .satisfies(dto -> {
                    assertThat(dto.getManualBatchPrecioVenta()).isNull();
                    assertThat(dto.getManualBatchCondicionFisica()).isNull();
                });
        assertThat(discoService.obtenerTodos(null, "manual:" + second.getId()))
                .filteredOn(dto -> dto.getIdDisco().equals(onlyInSecond.getIdDisco()))
                .singleElement()
                .satisfies(dto -> {
                    assertThat(dto.getManualBatchPrecioVenta()).isEqualByComparingTo("4000");
                    assertThat(dto.getManualBatchCondicionFisica()).isEqualTo("MINT");
                });
        assertThat(discoService.obtenerTodos(null, "manual:" + second.getId()))
                .extracting(dto -> dto.getIdDisco())
                .containsExactlyInAnyOrder(shared.getIdDisco(), onlyInSecond.getIdDisco());
    }

    private DiscogsManualBatch manualBatch(String customerCode, DiscogsManualBatchStatus status) {
        return manualBatchRepository.save(DiscogsManualBatch.builder()
                .customerCode(customerCode)
                .normalizedCustomerCode(customerCode)
                .status(status)
                .build());
    }

    private DiscoQrCopy copy(Disco product, DiscogsManualBatch batch, int number, String qr,
                             String price, String condition) {
        return DiscoQrCopy.builder()
                .idDisco(product.getIdDisco())
                .copyNumber(number)
                .codigoQr(qr + "-" + UUID.randomUUID())
                .estado(EstadoCopiaDisco.DISPONIBLE)
                .manualDiscogsBatch(batch)
                .precioVenta(new BigDecimal(price))
                .condicionFisica(condition)
                .build();
    }

    private Disco catalogProduct(int index) {
        return Disco.builder()
                .codigoQr("catalog-filter-" + index + "-" + UUID.randomUUID())
                .artista("Artista " + index)
                .album("Álbum " + index)
                .discogsReleaseId(100_000L + index)
                .estado(EstadoDisco.DISPONIBLE)
                .condicion(CondicionDisco.USADO)
                .tipoDisco(TipoDisco.VINILO)
                .build();
    }

    private DiscogsImportRow importedRow(DiscogsImportJob job, Disco product, int excelRow, String productResult) {
        return DiscogsImportRow.builder()
                .job(job)
                .sourceExcelRowNumber(excelRow)
                .discogsType("release")
                .discogsId(product.getDiscogsReleaseId())
                .resolvedReleaseId(product.getDiscogsReleaseId())
                .artist(product.getArtista())
                .title(product.getAlbum())
                .status(DiscogsImportRowStatus.IMPORTED)
                .catalogImportStatus(DiscogsCatalogImportStatus.IMPORTED)
                .catalogProductResult(productResult)
                .importedCatalogProduct(product)
                .build();
    }
}
