package com.sonograma.service.importacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonograma.dto.DiscogsImportJobDTO;
import com.sonograma.dto.DiscogsImportRowDTO;
import com.sonograma.dto.DiscogsCoverZipRow;
import com.sonograma.dto.DiscogsZipStatusDTO;
import com.sonograma.dto.TrackInfo;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscogsImportJob;
import com.sonograma.entity.DiscogsImportRow;
import com.sonograma.enums.*;
import com.sonograma.exception.NegocioException;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.DiscogsImportJobRepository;
import com.sonograma.repository.DiscogsImportRowRepository;
import com.sonograma.service.AudioPreviewService;
import com.sonograma.service.DiscoQrCopyService;
import com.sonograma.service.PreVentaCodeMatcher;
import com.sonograma.service.ImportMetadataNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscogsImportJobService {

    private static final String RATE_LIMIT_WARNING =
            "Esperando límite de Discogs. Metadata pendiente de reintento.";

    private final DiscogsExcelParser excelParser;
    private final DiscogsApiClient apiClient;
    private final DiscogsImportJobRepository jobRepository;
    private final DiscogsImportRowRepository rowRepository;
    private final DiscoRepository discoRepository;
    private final DiscogsCoverService coverService;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;
    private final AudioPreviewService audioPreviewService;
    private final DiscoQrCopyService qrCopyService;
    private final PreVentaCodeMatcher preVentaCodeMatcher;
    private final ExecutorService jobExecutor = Executors.newSingleThreadExecutor();

    public DiscogsImportJobDTO createJob(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new NegocioException("El archivo Excel está vacío");
        }

        DiscogsExcelParser.ParsedSheet parsed;
        String sourceFingerprint;
        try {
            sourceFingerprint = sha256(file.getBytes());
            parsed = excelParser.parse(file);
        } catch (IOException ex) {
            throw new NegocioException("No se pudo leer el Excel: " + ex.getMessage());
        }

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Long jobId = tx.execute(status -> {
            DiscogsImportJob job = DiscogsImportJob.builder()
                    .nombreArchivo(Optional.ofNullable(file.getOriginalFilename()).orElse("discogs.xlsx"))
                    .sourceFingerprint(sourceFingerprint)
                    .nombreHoja(parsed.sheetName())
                    .physicalExcelLastRow(parsed.physicalExcelLastRow())
                    .ignoredBlankRows(parsed.ignoredBlankRows())
                    .extraColumns(String.join("\n", parsed.extraColumns()))
                    .status(DiscogsImportJobStatus.PENDING)
                    .stage(DiscogsImportStage.PARSING_ROWS)
                    .build();
            for (DiscogsExcelParser.ParsedRow source : parsed.rows()) {
                boolean hasLink = source.discogsId() != null && source.discogsType() != null;
                DiscogsImportRow row = DiscogsImportRow.builder()
                        .job(job)
                        .sourceExcelRowNumber(source.sourceExcelRowNumber())
                        .visibleCellValue(source.visibleCellValue())
                        .hyperlinkUrl(source.hyperlinkUrl())
                        .normalizedDiscogsUrl(source.normalizedDiscogsUrl())
                        .urlSource(source.urlSource())
                        .discogsType(source.discogsType())
                        .discogsId(source.discogsId())
                        .artist(source.artist())
                        .title(source.title())
                        .rawCondition(source.rawCondition())
                        .manualCondition(source.manualCondition())
                        .rawPrice(source.rawPrice())
                        .manualPriceUyu(source.manualPriceUyu())
                        .manualGenre(source.manualGenre())
                        .observation(source.observation())
                        .sourceStatus(source.sourceStatus())
                        .internalCode(source.internalCode())
                        .status(source.status())
                        .errorMessage(null)
                        .warningMessage(source.errorMessage())
                        .metadataStatus(hasLink
                                ? DiscogsMetadataStatus.PENDING
                                : DiscogsMetadataStatus.MISSING_LINK)
                        .metadataErrorCode(hasLink ? null : "MISSING_DISCOGS_LINK")
                        .coverStatus(hasLink
                                ? DiscogsCoverStatus.PENDING
                                : DiscogsCoverStatus.NOT_APPLICABLE)
                        .youtubeStatus(hasLink
                                ? DiscogsYoutubeStatus.PENDING
                                : DiscogsYoutubeStatus.NOT_APPLICABLE)
                        .catalogImportStatus(initialCatalogStatus(source, hasLink))
                        .build();
                job.getRows().add(row);
            }
            return jobRepository.save(job).getIdDiscogsImportJob();
        });

        if (jobId == null) {
            throw new NegocioException("No se pudo crear el trabajo de importación");
        }
        log.info("Nueva importación Excel Discogs job={} fingerprint={} filas={}",
                jobId, sourceFingerprint, parsed.rows().size());
        jobExecutor.submit(() -> processJob(jobId));
        return getJob(jobId);
    }

    private DiscogsCatalogImportStatus initialCatalogStatus(DiscogsExcelParser.ParsedRow source,
                                                              boolean hasLink) {
        if (!hasLink && blank(source.artist()) && blank(source.title())) {
            return DiscogsCatalogImportStatus.MANUAL_REVIEW;
        }
        return hasLink ? DiscogsCatalogImportStatus.PENDING : DiscogsCatalogImportStatus.READY;
    }

    public DiscogsImportJobDTO getJob(Long jobId) {
        DiscogsImportJob job = jobRepository.findDetailedByIdDiscogsImportJob(jobId)
                .orElseThrow(() -> new NegocioException("Importación Discogs no encontrada: " + jobId));
        return toDto(job);
    }

    public DiscogsImportJobDTO retryRow(Long jobId, Long rowId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            DiscogsImportRow row = rowRepository.findById(rowId)
                    .orElseThrow(() -> new NegocioException("Fila Discogs no encontrada: " + rowId));
            if (!row.getJob().getIdDiscogsImportJob().equals(jobId)) {
                throw new NegocioException("La fila no pertenece a la importación indicada");
            }
            if (row.getDiscogsId() == null || row.getDiscogsType() == null) {
                throw new NegocioException("La fila no contiene un link Discogs válido");
            }
            row.setStatus(DiscogsImportRowStatus.PARSED);
            row.setErrorMessage(null);
            row.setMetadataStatus(DiscogsMetadataStatus.PENDING);
            row.setMetadataErrorCode(null);
            row.setCoverStatus(DiscogsCoverStatus.PENDING);
            row.setCoverErrorCode(null);
            row.setYoutubeStatus(DiscogsYoutubeStatus.PENDING);
            row.setYoutubeErrorCode(null);
            rowRepository.save(row);
            DiscogsImportJob job = row.getJob();
            job.setStatus(DiscogsImportJobStatus.PROCESSING);
            job.setErrorMessage(null);
            jobRepository.save(job);
        });
        jobExecutor.submit(() -> processRowAndFinalize(jobId, rowId));
        return getJob(jobId);
    }

    public DiscogsImportJobDTO retryPendingRows(Long jobId) {
        List<Long> rowIds = new TransactionTemplate(transactionManager).execute(status ->
                rowRepository.findByJobIdDiscogsImportJobAndStatusInOrderBySourceExcelRowNumber(
                                jobId,
                                List.of(
                                        DiscogsImportRowStatus.PENDING_RETRY,
                                        DiscogsImportRowStatus.RATE_LIMITED,
                                        DiscogsImportRowStatus.FAILED
                                )
                        ).stream()
                        .filter(row -> row.getDiscogsId() != null && row.getDiscogsType() != null)
                        .peek(row -> {
                            row.setStatus(DiscogsImportRowStatus.PARSED);
                            row.setErrorMessage(null);
                            row.setMetadataStatus(DiscogsMetadataStatus.PENDING);
                            row.setMetadataErrorCode(null);
                            row.setCoverStatus(DiscogsCoverStatus.PENDING);
                            row.setCoverErrorCode(null);
                            row.setYoutubeStatus(DiscogsYoutubeStatus.PENDING);
                            row.setYoutubeErrorCode(null);
                        })
                        .map(rowRepository::save)
                        .map(DiscogsImportRow::getIdDiscogsImportRow)
                        .toList()
        );
        updateJobStatus(jobId, DiscogsImportJobStatus.PROCESSING, null);
        jobExecutor.submit(() -> processRowsAndFinalize(jobId, rowIds == null ? List.of() : rowIds));
        return getJob(jobId);
    }

    public synchronized DiscogsImportJobDTO importParsedRows(Long jobId) {
        DiscogsImportJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new NegocioException("Importación Discogs no encontrada: " + jobId));
        ensurePostProcessingReady(job, "importar al catálogo");
        updateJobStage(jobId, DiscogsImportStage.IMPORTING_CATALOG);
        List<Long> rowIds = new TransactionTemplate(transactionManager).execute(status ->
                rowRepository.findByJobIdDiscogsImportJobOrderBySourceExcelRowNumber(jobId).stream()
                    .filter(this::isReadyToImport)
                    .map(DiscogsImportRow::getIdDiscogsImportRow)
                    .toList());
        for (Long rowId : Optional.ofNullable(rowIds).orElse(List.of())) {
            try {
                new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                        importRow(rowRepository.findById(rowId).orElseThrow()));
            } catch (RuntimeException ex) {
                markImportFailure(rowId, ex);
            }
        }
        finalizeJob(jobId);
        return getJob(jobId);
    }

    private void markImportFailure(Long rowId, RuntimeException ex) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            DiscogsImportRow row = rowRepository.findById(rowId).orElseThrow();
            row.setStatus(DiscogsImportRowStatus.FAILED);
            row.setErrorMessage(importError(row, ex));
            row.setCatalogImportStatus(DiscogsCatalogImportStatus.FAILED);
            row.setCatalogImportErrorCode("CATALOG_IMPORT_FAILED");
            rowRepository.save(row);
        });
    }

    private void importRow(DiscogsImportRow row) {
        try {
            List<DiscogsImportRow> priorImports = rowRepository.findPriorImportedPhysicalRows(
                    row.getJob().getSourceFingerprint(),
                    row.getJob().getIdDiscogsImportJob(),
                    row.getSourceExcelRowNumber()
            );
            if (!priorImports.isEmpty()) {
                row.setImportedCatalogProduct(priorImports.get(0).getImportedCatalogProduct());
                row.setStatus(DiscogsImportRowStatus.ALREADY_IMPORTED);
                row.setCatalogImportStatus(DiscogsCatalogImportStatus.ALREADY_IMPORTED);
                row.setCatalogImportErrorCode(null);
                rowRepository.save(row);
                return;
            }
            row.setCatalogImportStatus(DiscogsCatalogImportStatus.IMPORTING);
            if (row.getImportedCatalogProduct() != null) {
                updateDisco(row.getImportedCatalogProduct(), row);
                Disco disco = discoRepository.save(row.getImportedCatalogProduct());
                preVentaCodeMatcher.linkPendingPreSales(disco);
                qrCopyService.synchronize(disco);
                storeOptionalTracks(row, disco, parseTracks(row.getTracksJson()));
                row.setStatus(DiscogsImportRowStatus.IMPORTED);
                row.setCatalogImportStatus(DiscogsCatalogImportStatus.IMPORTED);
                row.setCatalogImportErrorCode(null);
                rowRepository.save(row);
                return;
            }
            Optional<Disco> existing = findExistingDisco(row);
            Disco disco = existing
                    .map(found -> mergeDisco(found, row))
                    .orElseGet(() -> toDisco(row));
            discoRepository.save(disco);
            preVentaCodeMatcher.linkPendingPreSales(disco);
            qrCopyService.synchronize(disco);
            storeOptionalTracks(row, disco, parseTracks(row.getTracksJson()));
            row.setImportedCatalogProduct(disco);
            row.setStatus(DiscogsImportRowStatus.IMPORTED);
            row.setCatalogImportStatus(DiscogsCatalogImportStatus.IMPORTED);
            row.setCatalogImportErrorCode(null);
            row.setErrorMessage(null);
            rowRepository.save(row);
        } catch (NegocioException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new NegocioException(importError(row, ex));
        }
    }

    public synchronized DiscogsZipStatusDTO prepareCoversZip(Long jobId) {
        DiscogsImportJob existing = jobRepository.findById(jobId)
                .orElseThrow(() -> new NegocioException("Importación Discogs no encontrada: " + jobId));
        if (existing.getZipStatus() == DiscogsZipStatus.PREPARING) {
            return toZipStatus(existing);
        }
        if (Set.of(DiscogsZipStatus.READY, DiscogsZipStatus.READY_WITH_WARNINGS)
                .contains(existing.getZipStatus())) {
            try {
                if (Files.isRegularFile(coverService.preparedZipPath(jobId))) {
                    return toZipStatus(existing);
                }
            } catch (IOException ex) {
                log.warn("DiscogsImport job={} stage=PREPARING_ZIP could not inspect existing ZIP: {}",
                        jobId, ex.getMessage());
            }
        }
        ensurePostProcessingReady(existing, "preparar el ZIP");

        List<DiscogsCoverZipRow> rows = zipRows(jobId);
        int total = (int) rows.stream()
                .filter(row -> row.getResolvedReleaseId() != null)
                .count();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            DiscogsImportJob job = jobRepository.findById(jobId).orElseThrow();
            job.setZipStatus(DiscogsZipStatus.PREPARING);
            job.setZipTotalCovers(total);
            job.setZipProcessedCovers(0);
            job.setZipAddedCovers(0);
            job.setZipFailedCovers(0);
            job.setZipCurrentRelease(null);
            job.setZipError(null);
            job.setZipFileName(null);
            job.setStage(DiscogsImportStage.PREPARING_ZIP);
            jobRepository.save(job);
        });
        jobExecutor.submit(() -> processZip(jobId, rows));
        return getCoversZipStatus(jobId);
    }

    private void ensurePostProcessingReady(DiscogsImportJob job, String action) {
        if (!Set.of(
                DiscogsImportJobStatus.COMPLETED,
                DiscogsImportJobStatus.COMPLETED_WITH_WARNINGS,
                DiscogsImportJobStatus.COMPLETED_WITH_ERRORS
        ).contains(job.getStatus())) {
            throw new NegocioException("La importación debe terminar antes de " + action);
        }
        if (Set.of(DiscogsImportStage.IMPORTING_CATALOG, DiscogsImportStage.PREPARING_ZIP)
                .contains(job.getStage())) {
            throw new NegocioException("La importación ya tiene otra operación en curso");
        }
    }

    public DiscogsZipStatusDTO getCoversZipStatus(Long jobId) {
        DiscogsImportJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new NegocioException("Importación Discogs no encontrada: " + jobId));
        return toZipStatus(job);
    }

    public Path getPreparedCoversZip(Long jobId) throws IOException {
        DiscogsImportJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new NegocioException("Importación Discogs no encontrada: " + jobId));
        if (!Set.of(DiscogsZipStatus.READY, DiscogsZipStatus.READY_WITH_WARNINGS)
                .contains(job.getZipStatus())) {
            throw new NegocioException("El ZIP de portadas todavía no está listo");
        }
        Path zip = coverService.preparedZipPath(jobId);
        if (!Files.isRegularFile(zip)) {
            markZipFailed(jobId, "ZIP_FILE_MISSING — El archivo preparado ya no está disponible");
            throw new NegocioException("El ZIP preparado ya no está disponible; generelo nuevamente");
        }
        return zip;
    }

    private void processZip(Long jobId, List<DiscogsCoverZipRow> rows) {
        try {
            Path target = coverService.preparedZipPath(jobId);
            DiscogsCoverService.ZipBuildResult result = coverService.buildZip(
                    target,
                    rows,
                    progress -> updateZipProgress(jobId, progress)
            );
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                if (!result.missingLocalRows().isEmpty()) {
                    rowRepository.findByJobIdDiscogsImportJobOrderBySourceExcelRowNumber(jobId).stream()
                            .filter(row -> result.missingLocalRows().contains(row.getSourceExcelRowNumber()))
                            .forEach(row -> {
                                row.setCoverStatus(DiscogsCoverStatus.MISSING_LOCAL_FILE);
                                row.setCoverErrorCode("MISSING_LOCAL_FILE");
                                appendWarning(row, "La portada local no estaba disponible al preparar el ZIP.");
                            });
                }
                DiscogsImportJob job = jobRepository.findById(jobId).orElseThrow();
                job.setZipTotalCovers(result.total());
                job.setZipProcessedCovers(result.total());
                job.setZipAddedCovers(result.added());
                job.setZipFailedCovers(result.failed());
                job.setZipCurrentRelease(null);
                job.setZipFileName(result.path().getFileName().toString());
                job.setZipStatus(result.failed() > 0 || result.warningCount() > 0
                        ? DiscogsZipStatus.READY_WITH_WARNINGS
                        : DiscogsZipStatus.READY);
                job.setZipError(null);
                job.setStage(DiscogsImportStage.COMPLETED);
                jobRepository.save(job);
            });
        } catch (Exception ex) {
            log.error("DiscogsImport job={} stage=PREPARING_ZIP failed: {}", jobId, ex.getMessage(), ex);
            markZipFailed(jobId, conciseError(ex));
        }
    }

    private void updateZipProgress(Long jobId, DiscogsCoverService.ZipProgress progress) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            DiscogsImportJob job = jobRepository.findById(jobId).orElseThrow();
            job.setZipTotalCovers(progress.total());
            job.setZipProcessedCovers(progress.processed());
            job.setZipAddedCovers(progress.added());
            job.setZipFailedCovers(progress.failed());
            job.setZipCurrentRelease(progress.currentRelease());
            jobRepository.save(job);
        });
    }

    private void markZipFailed(Long jobId, String error) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            DiscogsImportJob job = jobRepository.findById(jobId).orElseThrow();
            job.setZipStatus(DiscogsZipStatus.FAILED);
            job.setZipError(error);
            job.setZipCurrentRelease(null);
            job.setStage(DiscogsImportStage.COMPLETED);
            jobRepository.save(job);
        });
    }

    private List<DiscogsCoverZipRow> zipRows(Long jobId) {
        return new TransactionTemplate(transactionManager).execute(status ->
                rowRepository.findWithCatalogByJobIdDiscogsImportJobOrderBySourceExcelRowNumber(jobId)
                        .stream()
                        .map(row -> {
                            Disco catalog = row.getImportedCatalogProduct();
                            return DiscogsCoverZipRow.builder()
                                    .sourceExcelRowNumber(row.getSourceExcelRowNumber())
                                    .discogsUrl(row.getNormalizedDiscogsUrl())
                                    .sourceType(row.getDiscogsType())
                                    .sourceDiscogsId(row.getDiscogsId())
                                    .resolvedReleaseId(row.getResolvedReleaseId())
                                    .artist(row.getArtist())
                                    .title(row.getTitle())
                                    .priceUyu(row.getManualPriceUyu())
                                    .priceRaw(row.getRawPrice())
                                    .condition(row.getManualCondition())
                                    .sourceStatus(row.getSourceStatus())
                                    .metadataStatus(enumName(row.getMetadataStatus()))
                                    .metadataErrorCode(row.getMetadataErrorCode())
                                    .coverStatus(enumName(row.getCoverStatus()))
                                    .coverErrorCode(row.getCoverErrorCode())
                                    .youtubeStatus(enumName(row.getYoutubeStatus()))
                                    .catalogImportStatus(enumName(row.getCatalogImportStatus()))
                                    .imageUrl(row.getImageUrl())
                                    .coverLocalPath(row.getCoverLocalPath())
                                    .catalogDiscoId(catalog == null ? null : catalog.getIdDisco())
                                    .codigoQr(catalog == null ? null : catalog.getCodigoQr())
                                    .warningMessage(row.getWarningMessage())
                                    .errorMessage(row.getErrorMessage())
                                    .build();
                        })
                        .toList()
        );
    }

    private DiscogsZipStatusDTO toZipStatus(DiscogsImportJob job) {
        int total = Optional.ofNullable(job.getZipTotalCovers()).orElse(0);
        int processed = Optional.ofNullable(job.getZipProcessedCovers()).orElse(0);
        int progress = total == 0
                ? (Set.of(DiscogsZipStatus.READY, DiscogsZipStatus.READY_WITH_WARNINGS)
                        .contains(job.getZipStatus()) ? 100 : 0)
                : Math.min(100, (int) Math.round(processed * 100.0 / total));
        return DiscogsZipStatusDTO.builder()
                .jobId(job.getIdDiscogsImportJob())
                .zipStatus(enumName(job.getZipStatus()).toLowerCase(Locale.ROOT))
                .zipTotalCovers(total)
                .zipProcessedCovers(processed)
                .zipAddedCovers(Optional.ofNullable(job.getZipAddedCovers()).orElse(0))
                .zipFailedCovers(Optional.ofNullable(job.getZipFailedCovers()).orElse(0))
                .zipProgressPercentage(progress)
                .zipCurrentRelease(job.getZipCurrentRelease())
                .zipReady(Set.of(DiscogsZipStatus.READY, DiscogsZipStatus.READY_WITH_WARNINGS)
                        .contains(job.getZipStatus()))
                .zipError(job.getZipError())
                .build();
    }

    private void processJob(Long jobId) {
        try {
            updateJobStatus(jobId, DiscogsImportJobStatus.PROCESSING, null);
            updateJobStage(jobId, DiscogsImportStage.FETCHING_METADATA);
            List<Long> rowIds = new TransactionTemplate(transactionManager).execute(status ->
                    rowRepository.findByJobIdDiscogsImportJobAndStatusInOrderBySourceExcelRowNumber(
                                    jobId,
                                    List.of(
                                            DiscogsImportRowStatus.PARSED,
                                            DiscogsImportRowStatus.SOLD,
                                            DiscogsImportRowStatus.RESERVED
                                    )
                            ).stream()
                            .map(DiscogsImportRow::getIdDiscogsImportRow)
                            .toList()
            );
            if (rowIds != null) {
                processRows(rowIds, apiClient.newSession());
            }
            finalizeJob(jobId);
        } catch (Exception ex) {
            log.error("DiscogsImport job={} stage=JOB failed: {}", jobId, ex.getMessage(), ex);
            updateJobStatus(jobId, DiscogsImportJobStatus.FAILED, conciseError(ex));
        }
    }

    private void processRowAndFinalize(Long jobId, Long rowId) {
        try {
            processRow(rowId, apiClient.newSession());
        } catch (RuntimeException ex) {
            markUnexpectedRowFailure(rowId, ex);
        }
        finalizeJob(jobId);
    }

    private void processRowsAndFinalize(Long jobId, List<Long> rowIds) {
        try {
            processRows(rowIds, apiClient.newSession());
            finalizeJob(jobId);
        } catch (Exception ex) {
            log.error("Falló reintento Discogs {}: {}", jobId, ex.getMessage(), ex);
            updateJobStatus(jobId, DiscogsImportJobStatus.FAILED, ex.getMessage());
        }
    }

    private void processRows(List<Long> rowIds, DiscogsApiClient.ImportSession session) {
        for (Long rowId : rowIds) {
            try {
                processRow(rowId, session);
            } catch (RuntimeException ex) {
                markUnexpectedRowFailure(rowId, ex);
            }
        }
    }

    private void processRow(Long rowId, DiscogsApiClient.ImportSession session) {
        RowSource source = markMetadataProcessing(rowId);
        updateJobStage(source.jobId(), "master".equals(source.discogsType())
                ? DiscogsImportStage.RESOLVING_DISCOGS
                : DiscogsImportStage.FETCHING_METADATA);
        log.info("DiscogsImport job={} row={} sourceType={} sourceId={} resolvedRelease={} stage=FETCHING_METADATA retry={}",
                source.jobId(), source.rowNumber(), source.discogsType(), source.discogsId(),
                source.resolvedReleaseId(), source.retryCount());
        try {
            DiscogsApiClient.FetchResult result = apiClient.fetch(
                    session, source.discogsType(), source.discogsId());
            if (!result.success()) {
                handleMetadataFailure(rowId, source, result);
                return;
            }
            updateJobStage(source.jobId(), DiscogsImportStage.FETCHING_YOUTUBE);
            saveMetadataResult(rowId, result);

            try {
                updateJobStage(source.jobId(), DiscogsImportStage.DOWNLOADING_COVERS);
                setCoverDownloading(rowId);
                DiscogsCoverService.CoverResult cover = coverService.download(
                        result.imageUrl(), result.resolvedReleaseId());
                saveCoverResult(rowId, result, cover);
            } catch (RuntimeException coverFailure) {
                markCoverException(rowId, source, result.resolvedReleaseId(), coverFailure);
            }
        } catch (RuntimeException ex) {
            markMetadataException(rowId, source, ex);
        }
    }

    private RowSource markMetadataProcessing(Long rowId) {
        return new TransactionTemplate(transactionManager).execute(transactionStatus -> {
            DiscogsImportRow row = rowRepository.findById(rowId)
                    .orElseThrow(() -> new NegocioException("Fila Discogs no encontrada: " + rowId));
            row.setStatus(DiscogsImportRowStatus.FETCHING_DISCOGS);
            row.setMetadataStatus(DiscogsMetadataStatus.PROCESSING);
            row.setMetadataErrorCode(null);
            rowRepository.save(row);
            return new RowSource(
                    row.getJob().getIdDiscogsImportJob(),
                    row.getSourceExcelRowNumber(),
                    row.getDiscogsType(),
                    row.getDiscogsId(),
                    row.getResolvedReleaseId(),
                    row.getRetryCount()
            );
        });
    }

    private void handleMetadataFailure(Long rowId, RowSource source,
                                       DiscogsApiClient.FetchResult result) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            DiscogsImportRow row = rowRepository.findById(rowId).orElseThrow();
            if (result.rateLimited()) {
                row.setRetryCount(row.getRetryCount() + 1);
                row.setStatus(DiscogsImportRowStatus.PENDING_RETRY);
                row.setMetadataStatus(DiscogsMetadataStatus.RATE_LIMITED);
                row.setMetadataErrorCode("RATE_LIMITED");
                row.setErrorMessage(null);
                appendWarning(row, "MANUAL_REVIEW_REQUIRED — RATE_LIMITED — " + RATE_LIMIT_WARNING);
                row.setCoverStatus(DiscogsCoverStatus.PENDING);
                row.setYoutubeStatus(DiscogsYoutubeStatus.PENDING);
                row.setCatalogImportStatus(DiscogsCatalogImportStatus.READY);
            } else {
                String code = "master".equals(source.discogsType())
                        ? "MASTER_RESOLUTION_FAILED"
                        : "DISCOGS_METADATA_FAILED";
                row.setStatus(DiscogsImportRowStatus.PARSED);
                row.setMetadataStatus(DiscogsMetadataStatus.FAILED);
                row.setMetadataErrorCode(code);
                row.setErrorMessage(null);
                row.setCoverStatus(DiscogsCoverStatus.NOT_APPLICABLE);
                row.setYoutubeStatus(DiscogsYoutubeStatus.NOT_APPLICABLE);
                row.setCatalogImportStatus(DiscogsCatalogImportStatus.READY);
                appendWarning(row, "master".equals(source.discogsType())
                        ? "MANUAL_REVIEW_REQUIRED — MASTER_RESOLUTION_REVIEW_REQUIRED — "
                            + firstNonBlank(result.errorMessage(), "No se pudo resolver el master a un release.")
                        : "MANUAL_REVIEW_REQUIRED — DISCOGS_METADATA_UNAVAILABLE — "
                            + firstNonBlank(result.errorMessage(), "No se pudo obtener metadata de Discogs."));
            }
            rowRepository.save(row);
        });
        log.warn("DiscogsImport job={} row={} sourceType={} sourceId={} resolvedRelease={} stage=FETCHING_METADATA retry={} failed: {}",
                source.jobId(), source.rowNumber(), source.discogsType(), source.discogsId(),
                source.resolvedReleaseId(), source.retryCount() + (result.rateLimited() ? 1 : 0),
                result.errorMessage());
    }

    private void saveMetadataResult(Long rowId, DiscogsApiClient.FetchResult result) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            DiscogsImportRow row = rowRepository.findById(rowId).orElseThrow();
            row.setMasterId(result.masterId());
            row.setResolvedReleaseId(result.resolvedReleaseId());
            row.setArtist(firstNonBlank(result.artist(), row.getArtist()));
            row.setTitle(firstNonBlank(result.title(), row.getTitle()));
            row.setYear(result.year());
            row.setGenre(mergeGenres(result.genre(), row.getManualGenre()));
            row.setLabel(result.label());
            row.setCatalogNumber(result.catalogNumber());
            row.setCountry(result.country());
            row.setStyle(result.style());
            row.setFormat(result.format());
            row.setPreviewUrl(null);
            row.setTracklist(result.tracklist());
            List<TrackInfo> tracks = Optional.ofNullable(result.tracks()).orElse(List.of());
            int youtubeFound = (int) tracks.stream().filter(track -> !blank(track.youtubeUrl())).count();
            int youtubeMissing = (int) tracks.stream().filter(track -> blank(track.youtubeUrl())).count();
            try {
                row.setTracksJson(objectMapper.writeValueAsString(tracks));
            } catch (Exception ex) {
                row.setYoutubeStatus(DiscogsYoutubeStatus.FAILED);
                row.setYoutubeErrorCode("YOUTUBE_PERSISTENCE_FAILED");
                appendWarning(row, "No se pudieron persistir los links de YouTube.");
            }
            row.setYoutubeTracksFound(youtubeFound);
            row.setYoutubeTracksMissing(youtubeMissing);
            if (row.getYoutubeStatus() != DiscogsYoutubeStatus.FAILED) {
                row.setYoutubeStatus(youtubeFound == 0
                        ? DiscogsYoutubeStatus.NOT_FOUND
                        : youtubeMissing == 0
                            ? DiscogsYoutubeStatus.SUCCESS
                            : DiscogsYoutubeStatus.PARTIAL);
                row.setYoutubeErrorCode(youtubeFound == 0 ? "YOUTUBE_NOT_FOUND" : null);
            }
            row.setMetadataStatus(DiscogsMetadataStatus.SUCCESS);
            row.setMetadataErrorCode(null);
            if (row.getImportedCatalogProduct() != null) {
                updateDisco(row.getImportedCatalogProduct(), row);
                Disco disco = discoRepository.save(row.getImportedCatalogProduct());
                preVentaCodeMatcher.linkPendingPreSales(disco);
                qrCopyService.synchronize(disco);
                discoRepository.save(disco);
                storeOptionalTracks(row, disco, result.tracks());
                row.setStatus(DiscogsImportRowStatus.IMPORTED);
                row.setCatalogImportStatus(DiscogsCatalogImportStatus.IMPORTED);
            } else {
                row.setStatus(DiscogsImportRowStatus.PARSED);
                row.setCatalogImportStatus(DiscogsCatalogImportStatus.READY);
            }
            if (youtubeFound == 0) {
                appendWarning(row, "YOUTUBE_UNAVAILABLE — No se encontró un link de YouTube; la metadata Discogs sigue siendo válida.");
            }
            row.setErrorMessage(null);
            rowRepository.save(row);
            log.info("DiscogsImport job={} row={} sourceType={} sourceId={} resolvedRelease={} stage=FETCHING_METADATA retry={} success cache={}",
                    row.getJob().getIdDiscogsImportJob(), row.getSourceExcelRowNumber(), row.getDiscogsType(),
                    row.getDiscogsId(), result.resolvedReleaseId(), row.getRetryCount(), result.cacheHit());
        });
    }

    private void setCoverDownloading(Long rowId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            DiscogsImportRow row = rowRepository.findById(rowId).orElseThrow();
            row.setCoverStatus(DiscogsCoverStatus.DOWNLOADING);
            row.setCoverErrorCode(null);
            rowRepository.save(row);
        });
    }

    private void saveCoverResult(Long rowId, DiscogsApiClient.FetchResult metadata,
                                 DiscogsCoverService.CoverResult cover) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            DiscogsImportRow row = rowRepository.findById(rowId).orElseThrow();
            row.setImageUrl(cover.publicUrl());
            row.setCoverLocalPath(cover.localPath() == null ? null : cover.localPath().toString());
            if (cover.available()) {
                row.setCoverStatus(DiscogsCoverStatus.SUCCESS);
                row.setCoverErrorCode(null);
            } else if (metadata.imageUrl() == null || metadata.imageUrl().isBlank()) {
                row.setCoverStatus(DiscogsCoverStatus.UNAVAILABLE);
                row.setCoverErrorCode("COVER_UNAVAILABLE");
                appendWarning(row, "COVER_UNAVAILABLE — Portada no informada por Discogs.");
            } else {
                row.setCoverStatus(DiscogsCoverStatus.FAILED_RETRYABLE);
                row.setCoverErrorCode("COVER_DOWNLOAD_FAILED");
                appendWarning(row, "COVER_UNAVAILABLE — Portada no disponible: "
                        + firstNonBlank(cover.warning(), "error de descarga"));
            }
            rowRepository.save(row);
            if (!cover.available()) {
                log.warn("DiscogsImport job={} row={} sourceType={} sourceId={} resolvedRelease={} stage=COVER_DOWNLOAD retry={} failed: {}",
                        row.getJob().getIdDiscogsImportJob(), row.getSourceExcelRowNumber(), row.getDiscogsType(),
                        row.getDiscogsId(), metadata.resolvedReleaseId(), row.getRetryCount(), cover.warning());
            }
        });
    }

    private void markMetadataException(Long rowId, RowSource source, RuntimeException ex) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            DiscogsImportRow row = rowRepository.findById(rowId).orElseThrow();
            row.setStatus(DiscogsImportRowStatus.PARSED);
            row.setMetadataStatus(DiscogsMetadataStatus.FAILED);
            row.setMetadataErrorCode("DISCOGS_METADATA_FAILED");
            row.setErrorMessage(null);
            row.setCatalogImportStatus(DiscogsCatalogImportStatus.READY);
            appendWarning(row, "MANUAL_REVIEW_REQUIRED — DISCOGS_METADATA_UNAVAILABLE — " + conciseError(ex));
            rowRepository.save(row);
        });
        log.error("DiscogsImport job={} row={} sourceType={} sourceId={} resolvedRelease={} stage=FETCHING_METADATA retry={} failed: {}",
                source.jobId(), source.rowNumber(), source.discogsType(), source.discogsId(),
                source.resolvedReleaseId(), source.retryCount(), conciseError(ex), ex);
    }

    private void markCoverException(Long rowId, RowSource source, Long resolvedReleaseId,
                                    RuntimeException ex) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            DiscogsImportRow row = rowRepository.findById(rowId).orElseThrow();
            row.setCoverStatus(DiscogsCoverStatus.FAILED_RETRYABLE);
            row.setCoverErrorCode("COVER_DOWNLOAD_FAILED");
            appendWarning(row, "COVER_UNAVAILABLE — Portada no disponible: " + conciseError(ex));
            rowRepository.save(row);
        });
        log.error("DiscogsImport job={} row={} sourceType={} sourceId={} resolvedRelease={} stage=COVER_DOWNLOAD retry={} failed: {}",
                source.jobId(), source.rowNumber(), source.discogsType(), source.discogsId(),
                resolvedReleaseId, source.retryCount(), conciseError(ex), ex);
    }

    private void markUnexpectedRowFailure(Long rowId, RuntimeException ex) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                DiscogsImportRow row = rowRepository.findById(rowId).orElseThrow();
                row.setStatus(DiscogsImportRowStatus.PARSED);
                row.setMetadataStatus(DiscogsMetadataStatus.FAILED);
                row.setMetadataErrorCode("ROW_PROCESSING_FAILED");
                row.setErrorMessage(null);
                row.setCatalogImportStatus(canCreateMeaningfulCatalogProduct(row)
                        ? DiscogsCatalogImportStatus.READY
                        : DiscogsCatalogImportStatus.MANUAL_REVIEW);
                appendWarning(row, "MANUAL_REVIEW_REQUIRED — ROW_PROCESSING_FAILED — " + conciseError(ex));
                rowRepository.save(row);
            });
        } catch (RuntimeException persistenceFailure) {
            log.error("DiscogsImport row={} stage=ROW_ISOLATION could not persist failure: {}",
                    rowId, persistenceFailure.getMessage(), persistenceFailure);
        }
    }

    private void finalizeJob(Long jobId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            DiscogsImportJob job = jobRepository.findDetailedByIdDiscogsImportJob(jobId).orElseThrow();
            boolean warnings = job.getRows().stream().anyMatch(row ->
                    row.getMetadataStatus() != DiscogsMetadataStatus.SUCCESS
                            || row.getCoverStatus() != DiscogsCoverStatus.SUCCESS
                            || row.getYoutubeStatus() == DiscogsYoutubeStatus.NOT_FOUND
                            || row.getYoutubeStatus() == DiscogsYoutubeStatus.PARTIAL
                            || row.getYoutubeStatus() == DiscogsYoutubeStatus.FAILED
                            || row.getCatalogImportStatus() == DiscogsCatalogImportStatus.FAILED
                            || !blank(row.getWarningMessage())
                            || !blank(row.getErrorMessage()));
            job.setStatus(warnings
                    ? DiscogsImportJobStatus.COMPLETED_WITH_WARNINGS
                    : DiscogsImportJobStatus.COMPLETED);
            job.setStage(DiscogsImportStage.COMPLETED);
            jobRepository.save(job);
        });
    }

    private void updateJobStatus(Long jobId, DiscogsImportJobStatus status, String message) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(transactionStatus -> {
            DiscogsImportJob job = jobRepository.findById(jobId).orElseThrow();
            job.setStatus(status);
            job.setErrorMessage(message);
            jobRepository.save(job);
        });
    }

    private void updateJobStage(Long jobId, DiscogsImportStage stage) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            DiscogsImportJob job = jobRepository.findById(jobId).orElseThrow();
            job.setStage(stage);
            jobRepository.save(job);
        });
    }

    private void appendWarning(DiscogsImportRow row, String warning) {
        if (blank(warning)) return;
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (!blank(row.getWarningMessage())) values.add(row.getWarningMessage());
        values.add(warning);
        row.setWarningMessage(String.join(" ", values));
    }

    private String conciseError(Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (blank(message)) message = error == null ? "Error desconocido" : error.getClass().getSimpleName();
        message = message.replaceAll("\\s+", " ").trim();
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private String enumName(Enum<?> value) {
        return value == null ? "" : value.name();
    }

    private Disco toDisco(DiscogsImportRow row) {
        Disco disco = Disco.builder()
                .codigoInterno(firstNonBlank(row.getInternalCode(), generateCode(row)))
                .codigoQr(UUID.randomUUID().toString())
                .artista(partialArtist(row))
                .album(partialTitle(row))
                .genero(row.getGenre())
                .selloDiscografico(row.getLabel())
                .anio(row.getYear())
                .condicion(CondicionDisco.USADO)
                .condicionFisica(normalizePhysicalCondition(row.getManualCondition()))
                .tipoDisco(parseFormat(row.getFormat()))
                .formato(row.getFormat())
                .estado(EstadoDisco.DISPONIBLE)
                .cantidadCopias(1)
                .precioVenta(row.getManualPriceUyu())
                .pricingMode(row.getManualPriceUyu() != null ? PricingMode.MANUAL : PricingMode.AUTO)
                .pais(row.getCountry())
                .estilo(row.getStyle())
                .tracklist(row.getTracklist())
                .imagenUrl(row.getImageUrl())
                .previewUrl(null)
                .discogsUrl(row.getNormalizedDiscogsUrl())
                .procedencia(ImportMetadataNormalizer.SOURCE_DISCOGS)
                .notas(catalogNotes(row))
                .build();
        return disco;
    }

    private List<TrackInfo> parseTracks(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(
                json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, TrackInfo.class)
            );
        } catch (Exception ex) {
            log.warn("No se pudieron leer links de audio Discogs: {}", ex.getMessage());
            return List.of();
        }
    }

    private void storeOptionalTracks(DiscogsImportRow row, Disco disco, List<TrackInfo> tracks) {
        try {
            audioPreviewService.guardarDesdeTracks(disco.getIdDisco(), tracks);
        } catch (RuntimeException ex) {
            row.setYoutubeStatus(DiscogsYoutubeStatus.FAILED);
            row.setYoutubeErrorCode("YOUTUBE_ENRICHMENT_FAILED");
            appendWarning(row, "YOUTUBE_UNAVAILABLE — No se pudieron guardar los links opcionales: "
                    + conciseError(ex));
            disco.setNotas(mergeNotes(disco.getNotas(), catalogNotes(row)));
            log.warn("DiscogsImport job={} row={} optional audio/YouTube persistence failed: {}",
                    row.getJob().getIdDiscogsImportJob(), row.getSourceExcelRowNumber(), conciseError(ex));
        }
    }

    private void updateDisco(Disco disco, DiscogsImportRow row) {
        if (!blank(row.getArtist())) disco.setArtista(row.getArtist());
        if (!blank(row.getTitle())) disco.setAlbum(row.getTitle());
        if (!blank(row.getGenre())) disco.setGenero(row.getGenre());
        if (!blank(row.getLabel())) disco.setSelloDiscografico(row.getLabel());
        if (row.getYear() != null) disco.setAnio(row.getYear());
        if (!blank(row.getCountry())) disco.setPais(row.getCountry());
        if (!blank(row.getStyle())) disco.setEstilo(row.getStyle());
        if (!blank(row.getTracklist())) disco.setTracklist(row.getTracklist());
        if (!blank(row.getImageUrl())) disco.setImagenUrl(row.getImageUrl());
        disco.setPreviewUrl(null);
        // Discogs Excel imports are unconditionally used records. The spreadsheet
        // condition is a separate physical grade (NM, VG+, etc.).
        disco.setCondicion(CondicionDisco.USADO);
        if (!blank(row.getManualCondition())) {
            disco.setCondicionFisica(normalizePhysicalCondition(row.getManualCondition()));
        }
        disco.setProcedencia(firstNonBlank(
            ImportMetadataNormalizer.normalizeSource(disco.getProcedencia()),
            ImportMetadataNormalizer.SOURCE_DISCOGS
        ));
        disco.setEstado(EstadoDisco.DISPONIBLE);
        disco.setFormato(firstNonBlank(disco.getFormato(), row.getFormat()));
        if (!blank(row.getInternalCode())) disco.setCodigoInterno(row.getInternalCode());
        if (row.getManualPriceUyu() != null) {
            disco.setPrecioVenta(row.getManualPriceUyu());
            disco.setPricingMode(PricingMode.MANUAL);
        }
        disco.setTipoDisco(parseFormat(row.getFormat()));
        disco.setNotas(mergeNotes(disco.getNotas(), catalogNotes(row)));
    }

    private String normalizePhysicalCondition(String value) {
        if (blank(value)) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.length() <= 50 ? normalized : normalized.substring(0, 50);
    }

    private Optional<Disco> findExistingDisco(DiscogsImportRow row) {
        if (row.getResolvedReleaseId() != null) {
            Optional<Disco> fromImportedDiscogsRow = rowRepository
                    .findByResolvedReleaseIdAndImportedCatalogProductIsNotNullOrderByIdDiscogsImportRowDesc(
                            row.getResolvedReleaseId())
                    .stream()
                    .map(DiscogsImportRow::getImportedCatalogProduct)
                    .filter(Objects::nonNull)
                    .findFirst();
            if (fromImportedDiscogsRow.isPresent()) return fromImportedDiscogsRow;
        }
        if (!blank(row.getNormalizedDiscogsUrl())) {
            Optional<Disco> byUrl = discoRepository.findByDiscogsUrl(row.getNormalizedDiscogsUrl());
            if (byUrl.isPresent()) {
                return byUrl;
            }
        }
        if (row.getResolvedReleaseId() != null) {
            Optional<Disco> byRelease = discoRepository.findByDiscogsUrl(
                    "https://www.discogs.com/release/" + row.getResolvedReleaseId());
            if (byRelease.isPresent()) return byRelease;
        }
        boolean supplierOriginCode = isSupplierOriginCode(row.getInternalCode());
        if (!blank(row.getInternalCode()) && !supplierOriginCode) {
            Optional<Disco> byCode = discoRepository.findByCodigoInternoIgnoreCase(row.getInternalCode());
            if (byCode.isPresent()) {
                return byCode;
            }
        }
        if (supplierOriginCode) {
            return Optional.empty();
        }
        if (blank(row.getArtist()) || blank(row.getTitle())) {
            return Optional.empty();
        }
        String normalizedFormat = normalize(row.getFormat());
        return discoRepository.findByArtistaAndAlbumIgnoreCase(row.getArtist(), row.getTitle()).stream()
                .filter(candidate -> normalizedFormat.isBlank()
                        || normalize(candidate.getFormato()).isBlank()
                        || normalize(candidate.getFormato()).equals(normalizedFormat))
                .findFirst();
    }

    private Disco mergeDisco(Disco disco, DiscogsImportRow row) {
        updateDisco(disco, row);
        disco.setCantidadCopias(Math.max(0, Optional.ofNullable(disco.getCantidadCopias()).orElse(0)) + 1);
        return disco;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String catalogNotes(DiscogsImportRow row) {
        List<String> notes = new ArrayList<>();
        if (!blank(row.getCatalogNumber())) {
            notes.add("Número de catálogo Discogs: " + row.getCatalogNumber());
        }
        if (!blank(row.getManualCondition())) {
            notes.add("Condición física Excel: " + row.getManualCondition());
        }
        if (!blank(row.getSourceStatus())) {
            notes.add("Estado Excel: " + row.getSourceStatus());
        }
        if (!blank(row.getRawPrice()) && row.getManualPriceUyu() == null) {
            notes.add("Precio Excel no importado: " + row.getRawPrice());
        }
        if (!blank(row.getObservation())) {
            notes.add("Observación Excel: " + row.getObservation());
        }
        if (!blank(row.getWarningMessage())) {
            notes.add("Revisión importación Discogs: " + row.getWarningMessage());
        }
        return notes.isEmpty() ? null : String.join("\n", notes);
    }

    private String mergeNotes(String existing, String imported) {
        if (blank(existing)) return imported;
        if (blank(imported) || existing.contains(imported)) return existing;
        return existing + "\n" + imported;
    }

    private String mergeGenres(String discogsGenre, String spreadsheetGenre) {
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        for (String source : Arrays.asList(discogsGenre, spreadsheetGenre)) {
            if (blank(source)) continue;
            for (String value : source.split("[,;/|]")) {
                String clean = value.trim().replaceAll("\\s+", " ");
                if (!clean.isBlank()) unique.putIfAbsent(normalize(clean), clean);
            }
        }
        return unique.isEmpty() ? null : String.join(", ", unique.values());
    }

    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format(Locale.ROOT, "%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no está disponible", ex);
        }
    }

    private boolean isSupplierOriginCode(String code) {
        return !blank(code) && normalize(code).matches("[a-z]{2,4}");
    }

    private String importError(DiscogsImportRow row, RuntimeException ex) {
        return rowError(row, "No se pudo guardar el disco en el catálogo. "
                + firstNonBlank(ex.getMessage(), "Error interno durante la importación."));
    }

    private String rowError(DiscogsImportRow row, String explanation) {
        String value = firstNonBlank(row.getNormalizedDiscogsUrl(), row.getVisibleCellValue());
        return "Fila Excel " + row.getSourceExcelRowNumber()
                + ", columna LINK DE DISCOGS, valor \"" + firstNonBlank(value, "")
                + "\": " + firstNonBlank(explanation, "Error desconocido durante la importación.");
    }

    private String partialTitle(DiscogsImportRow row) {
        if (!blank(row.getTitle())) return row.getTitle();
        return row.getDiscogsId() == null
                ? "Título pendiente de revisión (fila Excel " + row.getSourceExcelRowNumber() + ")"
                : "Metadata pendiente (Discogs " + row.getDiscogsId() + ")";
    }

    private String partialArtist(DiscogsImportRow row) {
        if (!blank(row.getArtist())) return row.getArtist();
        return row.getDiscogsId() == null ? "Artista pendiente de revisión" : "Discogs pendiente";
    }

    private String generateCode(DiscogsImportRow row) {
        String initials = Arrays.stream(partialArtist(row).split("\\s+"))
                .filter(value -> !value.isBlank())
                .map(value -> value.substring(0, 1).toUpperCase(Locale.ROOT))
                .reduce("", String::concat);
        return (initials.isBlank() ? "XX" : initials)
                + "-" + Optional.ofNullable(row.getYear()).orElse(0)
                + "-" + Optional.ofNullable(row.getResolvedReleaseId())
                    .orElse(Optional.ofNullable(row.getDiscogsId())
                            .orElse(row.getSourceExcelRowNumber().longValue()));
    }

    private TipoDisco parseFormat(String format) {
        try {
            return TipoDisco.valueOf(Optional.ofNullable(format).orElse("VINILO"));
        } catch (IllegalArgumentException ex) {
            return TipoDisco.VINILO;
        }
    }

    private String firstNonBlank(String first, String fallback) {
        return blank(first) ? fallback : first;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isReadyToImport(DiscogsImportRow row) {
        return row.getImportedCatalogProduct() == null
                && row.getCatalogImportStatus() == DiscogsCatalogImportStatus.READY
                && canCreateMeaningfulCatalogProduct(row);
    }

    private boolean isReadyToImport(DiscogsImportRowDTO row) {
        return row.getImportedCatalogProductId() == null
                && "ready".equals(row.getCatalogImportStatus())
                && (row.getDiscogsId() != null || !blank(row.getArtist()) || !blank(row.getTitle()));
    }

    private boolean canCreateMeaningfulCatalogProduct(DiscogsImportRow row) {
        return row.getDiscogsId() != null || !blank(row.getArtist()) || !blank(row.getTitle());
    }

    private DiscogsImportJobDTO toDto(DiscogsImportJob job) {
        List<DiscogsImportRow> entityRows = job.getRows();
        List<DiscogsImportRowDTO> rows = job.getRows().stream().map(this::toRowDto).toList();
        DiscogsZipStatusDTO zip = toZipStatus(job);
        return DiscogsImportJobDTO.builder()
                .id(job.getIdDiscogsImportJob())
                .nombreArchivo(job.getNombreArchivo())
                .nombreHoja(job.getNombreHoja())
                .status(job.getStatus().name().toLowerCase(Locale.ROOT))
                .stage(enumName(job.getStage()).toLowerCase(Locale.ROOT))
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .physicalExcelLastRow(Optional.ofNullable(job.getPhysicalExcelLastRow()).orElse(0))
                .blankRowsIgnored(Optional.ofNullable(job.getIgnoredBlankRows()).orElse(0))
                .totalRowsRead(rows.size())
                .realRowsRead(rows.size())
                .validReleaseUrls(count(rows, row -> "release".equals(row.getDiscogsType())))
                .validMasterUrls(count(rows, row -> "master".equals(row.getDiscogsType())))
                .visibleDiscogsTextRows(count(rows, row -> row.getUrlSource() != null && row.getUrlSource().contains("visible_")))
                .directUrlRows(count(rows, row -> row.getUrlSource() != null && row.getUrlSource().endsWith("visible")))
                .sellReleaseUrlRows(count(rows, row ->
                        contains(row.getVisibleCellValue(), "/sell/release/")
                                || contains(row.getHyperlinkUrl(), "/sell/release/")))
                .embeddedHyperlinkRows(count(rows, row -> row.getHyperlinkUrl() != null))
                .needsManualMatch(countStatus(rows, DiscogsImportRowStatus.NEEDS_MANUAL_MATCH))
                .ignored(countStatus(rows, DiscogsImportRowStatus.IGNORED))
                .soldRows(count(rows, row -> "VENDIDO".equals(row.getSourceStatus())))
                .reservedRows(count(rows, row -> "RESERVADO".equals(row.getSourceStatus())))
                .availableRows(count(rows, row -> "DISPONIBLE".equals(row.getSourceStatus())))
                .invalidRows(count(rows, row -> "missing_link".equals(row.getMetadataStatus())))
                .linksDetected(count(rows, row -> row.getDiscogsId() != null))
                .missingDiscogsLinks(count(rows, row -> "missing_link".equals(row.getMetadataStatus())))
                .metadataFetched(count(rows, row -> "success".equals(row.getMetadataStatus())))
                .metadataPending(count(rows, row -> Set.of("pending", "processing", "rate_limited", "failed_retryable")
                        .contains(row.getMetadataStatus())))
                .metadataFailed(count(rows, row -> "failed".equals(row.getMetadataStatus())))
                .failed(countStatus(rows, DiscogsImportRowStatus.FAILED))
                .rateLimited(count(rows, row -> "rate_limited".equals(row.getMetadataStatus())))
                .imported(countStatus(rows, DiscogsImportRowStatus.IMPORTED))
                .alreadyImported(countStatus(rows, DiscogsImportRowStatus.ALREADY_IMPORTED))
                .coversDownloaded(count(rows, row -> "success".equals(row.getCoverStatus())))
                .coversMissing(count(rows, row -> Set.of("unavailable", "missing_local_file", "failed_retryable", "failed")
                        .contains(row.getCoverStatus())))
                .coversPending(count(rows, row -> Set.of("pending", "downloading").contains(row.getCoverStatus())))
                .mp3PreviewsFound(entityRows.stream()
                        .flatMap(row -> parseTracks(row.getTracksJson()).stream())
                        .mapToInt(track -> blank(track.mp3Url()) ? 0 : 1)
                        .sum())
                .youtubeLinksFound(entityRows.stream()
                        .flatMap(row -> parseTracks(row.getTracksJson()).stream())
                        .mapToInt(track -> blank(track.youtubeUrl()) ? 0 : 1)
                        .sum())
                .youtubeTracksMissing(entityRows.stream()
                        .mapToInt(row -> Optional.ofNullable(row.getYoutubeTracksMissing()).orElse(0))
                        .sum())
                .qrEntriesCreated(entityRows.stream()
                        .map(DiscogsImportRow::getImportedCatalogProduct)
                        .filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.toMap(
                                Disco::getIdDisco,
                                disco -> qrCopyService.listDtos(disco).size(),
                                Integer::max
                        ))
                        .values().stream().mapToInt(Integer::intValue).sum())
                .pending(count(rows, row -> Set.of("pending", "processing", "rate_limited", "failed_retryable")
                        .contains(row.getMetadataStatus())))
                .readyToImport(count(rows, this::isReadyToImport))
                .warnings(count(rows, row -> !blank(row.getWarningMessage())
                        || !blank(row.getErrorMessage())
                        || "missing_link".equals(row.getMetadataStatus())))
                .rowsDetected(rows.size())
                .rowsImported(count(rows, row -> row.getImportedCatalogProductId() != null))
                .rowsRequiringReview(count(rows, this::requiresReview))
                .rowsWithFullMetadata(count(rows, this::hasFullMetadata))
                .rowsWithWarnings(count(rows, this::requiresReview))
                .rowsTechnicallyImpossible(count(rows, row ->
                        "manual_review".equals(row.getCatalogImportStatus())
                                && row.getImportedCatalogProductId() == null
                                && row.getDiscogsId() == null
                                && blank(row.getArtist())
                                && blank(row.getTitle())))
                .zipStatus(zip.getZipStatus())
                .zipTotalCovers(zip.getZipTotalCovers())
                .zipProcessedCovers(zip.getZipProcessedCovers())
                .zipAddedCovers(zip.getZipAddedCovers())
                .zipFailedCovers(zip.getZipFailedCovers())
                .zipProgressPercentage(zip.getZipProgressPercentage())
                .zipCurrentRelease(zip.getZipCurrentRelease())
                .zipReady(zip.isZipReady())
                .zipError(zip.getZipError())
                .extraColumns(job.getExtraColumns() == null || job.getExtraColumns().isBlank()
                        ? List.of()
                        : Arrays.stream(job.getExtraColumns().split("\\R"))
                                .map(String::trim)
                                .filter(value -> !value.isBlank())
                                .toList())
                .rows(rows)
                .build();
    }

    private DiscogsImportRowDTO toRowDto(DiscogsImportRow row) {
        return DiscogsImportRowDTO.builder()
                .id(row.getIdDiscogsImportRow())
                .sourceExcelRowNumber(row.getSourceExcelRowNumber())
                .visibleCellValue(row.getVisibleCellValue())
                .hyperlinkUrl(row.getHyperlinkUrl())
                .normalizedDiscogsUrl(row.getNormalizedDiscogsUrl())
                .urlSource(row.getUrlSource())
                .discogsType(row.getDiscogsType())
                .discogsId(row.getDiscogsId())
                .masterId(row.getMasterId())
                .resolvedReleaseId(row.getResolvedReleaseId())
                .artist(row.getArtist())
                .title(row.getTitle())
                .rawCondition(row.getRawCondition())
                .manualCondition(row.getManualCondition())
                .rawPrice(row.getRawPrice())
                .manualPriceUyu(row.getManualPriceUyu())
                .manualGenre(row.getManualGenre())
                .observation(row.getObservation())
                .sourceStatus(row.getSourceStatus())
                .internalCode(row.getInternalCode())
                .year(row.getYear())
                .genre(row.getGenre())
                .label(row.getLabel())
                .catalogNumber(row.getCatalogNumber())
                .country(row.getCountry())
                .style(row.getStyle())
                .format(row.getFormat())
                .tracklist(row.getTracklist())
                .youtubeLinksFound(Optional.ofNullable(row.getYoutubeTracksFound()).orElse(0))
                .youtubeTracksMissing(Optional.ofNullable(row.getYoutubeTracksMissing()).orElse(0))
                .imageUrl(row.getImageUrl())
                .metadataStatus(enumName(row.getMetadataStatus()).toLowerCase(Locale.ROOT))
                .metadataErrorCode(row.getMetadataErrorCode())
                .coverStatus(enumName(row.getCoverStatus()).toLowerCase(Locale.ROOT))
                .coverErrorCode(row.getCoverErrorCode())
                .youtubeStatus(enumName(row.getYoutubeStatus()).toLowerCase(Locale.ROOT))
                .youtubeErrorCode(row.getYoutubeErrorCode())
                .catalogImportStatus(enumName(row.getCatalogImportStatus()).toLowerCase(Locale.ROOT))
                .catalogImportErrorCode(row.getCatalogImportErrorCode())
                .warningMessage(row.getWarningMessage())
                .status(row.getStatus().name().toLowerCase(Locale.ROOT))
                .errorMessage(row.getErrorMessage())
                .retryCount(row.getRetryCount())
                .importedCatalogProductId(row.getImportedCatalogProduct() != null
                        ? row.getImportedCatalogProduct().getIdDisco()
                        : null)
                .build();
    }

    private int countStatus(List<DiscogsImportRowDTO> rows, DiscogsImportRowStatus status) {
        return count(rows, row -> status.name().equalsIgnoreCase(row.getStatus()));
    }

    private int count(List<DiscogsImportRowDTO> rows, java.util.function.Predicate<DiscogsImportRowDTO> test) {
        return (int) rows.stream().filter(test).count();
    }

    private boolean requiresReview(DiscogsImportRowDTO row) {
        return !blank(row.getWarningMessage())
                || !blank(row.getErrorMessage())
                || !"success".equals(row.getMetadataStatus())
                || !Set.of("success", "not_applicable").contains(row.getCoverStatus())
                || !Set.of("success", "not_applicable").contains(row.getYoutubeStatus())
                || "manual_review".equals(row.getCatalogImportStatus())
                || "failed".equals(row.getCatalogImportStatus());
    }

    private boolean hasFullMetadata(DiscogsImportRowDTO row) {
        return "success".equals(row.getMetadataStatus())
                && "success".equals(row.getCoverStatus())
                && "success".equals(row.getYoutubeStatus())
                && !requiresReview(row);
    }

    private boolean contains(String value, String needle) {
        return value != null && value.contains(needle);
    }

    @PreDestroy
    void shutdown() {
        jobExecutor.shutdownNow();
    }

    private record RowSource(
            Long jobId,
            Integer rowNumber,
            String discogsType,
            Long discogsId,
            Long resolvedReleaseId,
            Integer retryCount
    ) {}
}
