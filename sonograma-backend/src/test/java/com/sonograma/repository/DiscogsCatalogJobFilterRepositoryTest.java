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
import com.sonograma.dto.DiscoResponseDTO;
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
    void manualSourcesGroupTechnicalBatchesByNormalizedCustomerAndFilterAllCopies() {
        DiscogsManualBatch first = manualBatchRepository.save(manualBatch("JPH", DiscogsManualBatchStatus.OPEN));
        DiscogsManualBatch second = manualBatchRepository.save(manualBatch("jph", DiscogsManualBatchStatus.FINALIZED));
        DiscogsManualBatch otherCustomer = manualBatchRepository.save(manualBatch("SV3", DiscogsManualBatchStatus.FINALIZED));
        Disco shared = discoRepository.save(catalogProduct(3000));
        Disco onlyInSecond = discoRepository.save(catalogProduct(3001));

        copyRepository.saveAll(List.of(
                copy(shared, first, 1, "first-a", "1000", "VG"),
                copy(shared, second, 2, "second-a", "3000", "NM"),
                copy(onlyInSecond, second, 3, "second-b", "4000", "MINT"),
                copy(shared, otherCustomer, 4, "other-a", "5000", "EX")
        ));

        List<DiscogsCatalogSourceDTO> sources = discoService.listarFuentesImportacionDiscogs();

        assertThat(sources).filteredOn(source -> source.type().equals("MANUAL"))
                .hasSize(2);
        assertThat(discoService.listarFuentesImportacionDiscogs())
                .filteredOn(source -> source.key().equals("manual:customer:JPH"))
                .singleElement()
                .satisfies(source -> {
                    assertThat(source.label()).isEqualTo("JPH · 3 discos · En curso");
                    assertThat(source.status()).isEqualTo(DiscogsManualBatchStatus.OPEN);
                    assertThat(source.batchId()).isEqualTo(first.getId());
                });
        assertThat(discoService.listarFuentesImportacionDiscogs())
                .filteredOn(source -> source.key().equals("manual:customer:SV3"))
                .singleElement()
                .extracting(DiscogsCatalogSourceDTO::label)
                .isEqualTo("SV3 · 1 discos · Finalizada");
        assertThat(discoService.obtenerTodos(null, "manual:customer:jph"))
                .extracting(dto -> dto.getIdDisco())
                .containsExactlyInAnyOrder(shared.getIdDisco(), onlyInSecond.getIdDisco());
        assertThat(discoService.obtenerTodos(null, "manual:customer:JPH"))
                .filteredOn(dto -> dto.getIdDisco().equals(shared.getIdDisco()))
                .singleElement()
                .satisfies(dto -> {
                    assertThat(dto.getManualBatchCustomerCode()).isEqualTo("JPH");
                    assertThat(dto.getCodigoInterno()).isEqualTo("INTERNAL-3000");
                    assertThat(dto.getManualBatchPrecioVenta()).isNull();
                    assertThat(dto.getManualBatchCondicionFisica()).isNull();
                });
        assertThat(discoService.obtenerTodos(null, "manual:customer:JPH"))
                .filteredOn(dto -> dto.getIdDisco().equals(onlyInSecond.getIdDisco()))
                .singleElement()
                .satisfies(dto -> {
                    assertThat(dto.getManualBatchCustomerCode()).isEqualTo("JPH");
                    assertThat(dto.getManualBatchPrecioVenta()).isEqualByComparingTo("4000");
                    assertThat(dto.getManualBatchCondicionFisica()).isEqualTo("MINT");
                });
        assertThat(discoService.obtenerTodos(null, "manual:customer:SV3"))
                .filteredOn(dto -> dto.getIdDisco().equals(shared.getIdDisco()))
                .singleElement()
                .extracting(DiscoResponseDTO::getManualBatchCustomerCode)
                .isEqualTo("SV3");
        // Legacy technical selectors remain exact and do not leak the logical grouping.
        assertThat(discoService.obtenerTodos(null, "manual:" + first.getId()))
                .extracting(DiscoResponseDTO::getIdDisco)
                .containsExactly(shared.getIdDisco());
    }

    @Test
    void historicalJsBatchesWithOnePlusOnePlusOnePlusFiftyOneCopiesExposeOneLogicalSource() {
        List<DiscogsManualBatch> jsBatches = List.of(
                manualBatch("JS", DiscogsManualBatchStatus.FINALIZED),
                manualBatch("js", DiscogsManualBatchStatus.FINALIZED),
                manualBatch(" JS ", DiscogsManualBatchStatus.FINALIZED),
                manualBatch("Js", DiscogsManualBatchStatus.FINALIZED)
        );
        Disco product = discoRepository.save(catalogProduct(3100));

        List<DiscoQrCopy> copies = new ArrayList<>();
        int copyNumber = 1;
        for (int batchIndex = 0; batchIndex < jsBatches.size(); batchIndex++) {
            int batchCopies = batchIndex == jsBatches.size() - 1 ? 51 : 1;
            for (int index = 0; index < batchCopies; index++) {
                copies.add(copy(product, jsBatches.get(batchIndex), copyNumber++,
                        "js-history-" + copyNumber, "1000", "VG"));
            }
        }
        copyRepository.saveAll(copies);

        List<DiscogsCatalogSourceDTO> manualSources = discoService.listarFuentesImportacionDiscogs().stream()
                .filter(source -> source.type().equals("MANUAL"))
                .toList();

        assertThat(manualSources).singleElement().satisfies(source -> {
            assertThat(source.key()).isEqualTo("manual:customer:JS");
            assertThat(source.customerCode()).isEqualTo("JS");
            assertThat(source.productos()).isEqualTo(54L);
            assertThat(source.status()).isEqualTo(DiscogsManualBatchStatus.FINALIZED);
        });
        assertThat(discoService.obtenerTodos(null, "manual:customer:js"))
                .extracting(DiscoResponseDTO::getIdDisco)
                .containsExactly(product.getIdDisco());
    }

    @Test
    void generalSearchPrioritizesExactManualCustomerMembershipAcrossBatchesWithoutDuplicates() {
        DiscogsManualBatch jsFinalized = manualBatch("JS", DiscogsManualBatchStatus.FINALIZED);
        DiscogsManualBatch jsOpen = manualBatch(" js ", DiscogsManualBatchStatus.OPEN);
        DiscogsManualBatch sv3Finalized = manualBatch("SV3", DiscogsManualBatchStatus.FINALIZED);

        Disco jsOnly = discoRepository.save(catalogProduct(3200));
        Disco shared = discoRepository.save(catalogProduct(3201));
        Disco sv3Only = discoRepository.save(catalogProduct(3202));
        Disco accidentalJs = catalogProduct(3203);
        accidentalJs.setArtista("Artist JS accidental");
        accidentalJs.setCodigoInterno("UNRELATED-3203");
        accidentalJs = discoRepository.save(accidentalJs);
        Disco ordinaryJs2 = catalogProduct(3204);
        ordinaryJs2.setCodigoInterno("JS2-3204");
        ordinaryJs2 = discoRepository.save(ordinaryJs2);

        copyRepository.saveAll(List.of(
                copy(jsOnly, jsFinalized, 1, "search-js-only", "1000", "VG"),
                copy(shared, jsFinalized, 1, "search-js-shared-finalized", "1000", "VG"),
                copy(shared, jsOpen, 2, "search-js-shared-open", "1000", "VG"),
                copy(shared, sv3Finalized, 3, "search-sv3-shared", "1000", "VG"),
                copy(sv3Only, sv3Finalized, 1, "search-sv3-only", "1000", "VG")
        ));

        List<DiscoResponseDTO> jsResults = discoService.buscar(" JS ");
        assertThat(jsResults).extracting(DiscoResponseDTO::getIdDisco)
                .containsExactlyInAnyOrder(jsOnly.getIdDisco(), shared.getIdDisco())
                .doesNotContain(accidentalJs.getIdDisco(), ordinaryJs2.getIdDisco(), sv3Only.getIdDisco());
        assertThat(jsResults).extracting(DiscoResponseDTO::getIdDisco).doesNotHaveDuplicates();
        assertThat(jsResults).allSatisfy(result ->
                assertThat(result.getManualBatchCustomerCode()).isEqualTo("JS"));

        List<DiscoResponseDTO> sv3Results = discoService.buscar(" Sv3 ");
        assertThat(sv3Results).extracting(DiscoResponseDTO::getIdDisco)
                .containsExactlyInAnyOrder(shared.getIdDisco(), sv3Only.getIdDisco())
                .doesNotContain(jsOnly.getIdDisco(), accidentalJs.getIdDisco());
        assertThat(sv3Results).extracting(DiscoResponseDTO::getIdDisco).doesNotHaveDuplicates();
        assertThat(sv3Results).allSatisfy(result ->
                assertThat(result.getManualBatchCustomerCode()).isEqualTo("SV3"));

        // An unknown code keeps the pre-existing ordinary product-field search.
        assertThat(discoService.buscar("JS2"))
                .extracting(DiscoResponseDTO::getIdDisco)
                .containsExactly(ordinaryJs2.getIdDisco());
        assertThat(discoService.buscar("Artist JS accidental"))
                .extracting(DiscoResponseDTO::getIdDisco)
                .containsExactly(accidentalJs.getIdDisco());
    }

    @Test
    void ordinarySearchFieldsRemainAvailableForNonCustomerQueries() {
        Disco fp = catalogProduct(3210);
        fp.setCodigoInterno("FP-3210");
        fp.setArtista("Ordinary Artist");
        fp.setAlbum("Ordinary Album");
        fp = discoRepository.save(fp);

        Disco lvs = catalogProduct(3211);
        lvs.setCodigoInterno("LVS-3211");
        lvs = discoRepository.save(lvs);

        assertThat(discoService.buscar("FP"))
                .extracting(DiscoResponseDTO::getIdDisco)
                .containsExactly(fp.getIdDisco());
        assertThat(discoService.buscar("LVS"))
                .extracting(DiscoResponseDTO::getIdDisco)
                .containsExactly(lvs.getIdDisco());
        assertThat(discoService.buscar("Ordinary Artist"))
                .extracting(DiscoResponseDTO::getIdDisco)
                .containsExactly(fp.getIdDisco());
        assertThat(discoService.buscar("Ordinary Album"))
                .extracting(DiscoResponseDTO::getIdDisco)
                .containsExactly(fp.getIdDisco());
    }

    private DiscogsManualBatch manualBatch(String customerCode, DiscogsManualBatchStatus status) {
        return manualBatchRepository.save(DiscogsManualBatch.builder()
                .customerCode(customerCode)
                .normalizedCustomerCode(customerCode.trim().toUpperCase(java.util.Locale.ROOT))
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
                .codigoInterno("INTERNAL-" + index)
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
