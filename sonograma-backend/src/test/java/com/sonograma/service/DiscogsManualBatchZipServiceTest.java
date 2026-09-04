package com.sonograma.service;

import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.entity.DiscogsManualBatch;
import com.sonograma.enums.DiscogsManualBatchStatus;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.repository.DiscoQrCopyRepository;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.DiscogsManualBatchRepository;
import com.sonograma.service.importacion.DiscogsCoverService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscogsManualBatchZipServiceTest {

    @TempDir
    Path tempDir;

    private final DiscogsManualBatchRepository batchRepository = mock(DiscogsManualBatchRepository.class);
    private final DiscoQrCopyRepository copyRepository = mock(DiscoQrCopyRepository.class);
    private final DiscoRepository discoRepository = mock(DiscoRepository.class);
    private final DiscogsCoverService coverService = mock(DiscogsCoverService.class);
    private final QRService qrService = mock(QRService.class);
    private final AudioPreviewService audioPreviewService = mock(AudioPreviewService.class);
    private final DiscogsManualBatchZipService service = new DiscogsManualBatchZipService(
            batchRepository, copyRepository, discoRepository, coverService, qrService, audioPreviewService);

    @Test
    void exportsOnlyExactBatchCopiesAndKeepsDuplicateProductCopiesDistinct() throws Exception {
        DiscogsManualBatch selectedBatch = batch(15L, DiscogsManualBatchStatus.FINALIZED);
        Disco first = product(10L, 111L, "Artist One", "Album One");
        Disco second = product(20L, 222L, "Artist Two", "Album Two");
        DiscoQrCopy copyA = copy(100L, first, 1, "qr-a");
        DiscoQrCopy copyB = copy(101L, first, 2, "qr-b");
        DiscoQrCopy copyC = copy(102L, second, 1, "qr-c");
        DiscoQrCopy outsideBatch = copy(999L, first, 3, "qr-outside");
        Path cover = tempDir.resolve("111.jpg");
        Files.write(cover, new byte[]{1, 2, 3});

        when(batchRepository.findById(15L)).thenReturn(Optional.of(selectedBatch));
        when(copyRepository.findByManualDiscogsBatchIdOrderByCopyNumber(15L))
                .thenReturn(List.of(copyA, copyB, copyC));
        when(discoRepository.findAllById(anyList())).thenReturn(List.of(first, second));
        when(coverService.existing(any())).thenReturn(new DiscogsCoverService.CoverResult(
                true, false, "/api/importaciones/discogs/covers/111.jpg", cover, null));
        when(qrService.generarQRParaCopia(any(), any())).thenAnswer(invocation ->
                invocation.getArgument(1, DiscoQrCopy.class).getCodigoQr().getBytes(StandardCharsets.UTF_8));
        when(audioPreviewService.listarPorDisco(any())).thenReturn(List.of());

        DiscogsManualBatchZipService.GeneratedZip generated = service.generate(15L);

        assertThat(generated.filename()).isEqualTo("JPH_2026-09-04_batch-15.zip");
        Set<String> entries = readEntries(generated.content());
        assertThat(entries).contains(
                "discogs-summary.csv",
                "releases/release-111-disco-10/cover.jpg",
                "releases/release-111-disco-10/release.json",
                "releases/release-111-disco-10/tracks-and-links.txt",
                "releases/release-222-disco-20/release.json",
                "qr/copy-100-disco-10-copy-1.png",
                "qr/copy-101-disco-10-copy-2.png",
                "qr/copy-102-disco-20-copy-1.png");
        assertThat(entries).noneMatch(name -> name.contains("999") || name.contains("outside"));
        assertThat(readEntry(generated.content(), "qr/copy-100-disco-10-copy-1.png"))
                .containsExactly("qr-a".getBytes(StandardCharsets.UTF_8));
        assertThat(readEntry(generated.content(), "qr/copy-101-disco-10-copy-2.png"))
                .containsExactly("qr-b".getBytes(StandardCharsets.UTF_8));
        String summary = new String(readEntry(generated.content(), "discogs-summary.csv"), StandardCharsets.UTF_8);
        assertThat(summary).contains("\"100\"").contains("\"101\"").contains("\"102\"")
                .doesNotContain("999");
        verify(qrService).generarQRParaCopia(first, copyA);
        verify(qrService).generarQRParaCopia(first, copyB);
        verify(qrService).generarQRParaCopia(second, copyC);
        verify(qrService, never()).generarQRParaCopia(first, outsideBatch);
    }

    @Test
    void missingOptionalCoverDoesNotAbortOrDownloadFromDiscogs() throws Exception {
        DiscogsManualBatch selectedBatch = batch(16L, DiscogsManualBatchStatus.OPEN);
        Disco product = product(30L, 333L, "Artist", "Album");
        DiscoQrCopy copy = copy(103L, product, 1, "qr-missing-cover");
        when(batchRepository.findById(16L)).thenReturn(Optional.of(selectedBatch));
        when(copyRepository.findByManualDiscogsBatchIdOrderByCopyNumber(16L)).thenReturn(List.of(copy));
        when(discoRepository.findAllById(anyList())).thenReturn(List.of(product));
        when(coverService.existing(any())).thenReturn(new DiscogsCoverService.CoverResult(
                false, false, null, null, "La portada aún no está disponible localmente."));
        when(qrService.generarQRParaCopia(any(), any())).thenReturn(new byte[]{7});
        when(audioPreviewService.listarPorDisco(any())).thenReturn(List.of());

        DiscogsManualBatchZipService.GeneratedZip generated = service.generate(16L);

        assertThat(readEntries(generated.content())).contains("errors.csv");
        assertThat(new String(readEntry(generated.content(), "errors.csv"), StandardCharsets.UTF_8))
                .contains("MISSING_LOCAL_FILE");
        verify(coverService, never()).download(any(String.class), anyLong());
    }

    @Test
    void rejectsInvalidAndZeroCopyBatches() {
        when(batchRepository.findById(17L)).thenReturn(Optional.of(batch(17L, DiscogsManualBatchStatus.OPEN)));
        when(copyRepository.findByManualDiscogsBatchIdOrderByCopyNumber(17L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.generate(null))
                .hasMessage("El batch Discogs no es válido.");
        assertThatThrownBy(() -> service.generate(17L))
                .hasMessage("El batch Discogs no tiene copias físicas para exportar.");
        assertThatThrownBy(() -> service.generate(18L))
                .hasMessage("Batch Discogs no encontrado con id: 18");
    }

    private DiscogsManualBatch batch(Long id, DiscogsManualBatchStatus status) {
        LocalDateTime timestamp = LocalDateTime.of(2026, 9, 4, 10, 0);
        return DiscogsManualBatch.builder()
                .id(id)
                .customerCode("JPH")
                .normalizedCustomerCode("JPH")
                .status(status)
                .startedAt(timestamp)
                .createdAt(timestamp)
                .updatedAt(timestamp)
                .build();
    }

    private Disco product(Long id, Long releaseId, String artist, String album) {
        return Disco.builder()
                .idDisco(id)
                .discogsReleaseId(releaseId)
                .discogsUrl("https://www.discogs.com/release/" + releaseId)
                .artista(artist)
                .album(album)
                .genero("House")
                .tracklist("A1 - Track")
                .imagenUrl("/api/importaciones/discogs/covers/" + releaseId + ".jpg")
                .build();
    }

    private DiscoQrCopy copy(Long id, Disco product, int number, String qr) {
        return DiscoQrCopy.builder()
                .id(id)
                .idDisco(product.getIdDisco())
                .copyNumber(number)
                .codigoQr(qr)
                .estado(EstadoCopiaDisco.DISPONIBLE)
                .build();
    }

    private Set<String> readEntries(byte[] content) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
            Set<String> entries = new java.util.LinkedHashSet<>();
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) entries.add(entry.getName());
            return entries;
        }
    }

    private byte[] readEntry(byte[] content, String name) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                if (entry.getName().equals(name)) return zip.readAllBytes();
            }
        }
        throw new AssertionError("ZIP entry not found: " + name);
    }
}
