package com.sonograma.service.demo;

import com.sonograma.dto.DiscoQrCopyDTO;
import com.sonograma.dto.DiscoRequestDTO;
import com.sonograma.dto.DiscoResponseDTO;
import com.sonograma.dto.TrackInfo;
import com.sonograma.dto.VinylPageData;
import com.sonograma.entity.Disco;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.service.AudioPreviewService;
import com.sonograma.service.DiscoQrCopyService;
import com.sonograma.service.DiscoService;
import com.sonograma.service.QRService;
import com.sonograma.service.VinylFutureAssetService;
import com.sonograma.service.VinylFutureScraperService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VinylFutureCqtr005DemoSeedServiceTest {

    private DiscoRepository discoRepository;
    private VinylFutureScraperService scraperService;
    private VinylFutureAssetService assetService;
    private DiscoService discoService;
    private AudioPreviewService audioPreviewService;
    private DiscoQrCopyService qrCopyService;
    private QRService qrService;
    private VinylFutureCqtr005DemoSeedService seedService;

    @BeforeEach
    void setUp() {
        discoRepository = mock(DiscoRepository.class);
        scraperService = mock(VinylFutureScraperService.class);
        assetService = mock(VinylFutureAssetService.class);
        discoService = mock(DiscoService.class);
        audioPreviewService = mock(AudioPreviewService.class);
        qrCopyService = mock(DiscoQrCopyService.class);
        qrService = mock(QRService.class);
        seedService = new VinylFutureCqtr005DemoSeedService(
            discoRepository,
            scraperService,
            assetService,
            discoService,
            audioPreviewService,
            qrCopyService,
            qrService
        );
    }

    @Test
    void createsOnePricelessFutureRecordWithExistingStockMediaAndQrServices() {
        VinylPageData scraped = page(
            "https://www.deejay.de/images/xl/5/5/1133955.jpg",
            "https://www.deejay.de/streamit/5/5/1133955a.mp3"
        );
        VinylPageData stored = page(
            "/api/importar/vinylfuture/media/CQTR005/cover.jpg",
            "/api/importar/vinylfuture/media/CQTR005/A1.mp3"
        );
        Disco saved = Disco.builder()
            .idDisco(708L)
            .codigoInterno("CQTR005")
            .discogsUrl(VinylFutureCqtr005DemoSeedService.PRODUCT_URL)
            .build();
        DiscoQrCopyDTO copy = new DiscoQrCopyDTO(
            901L,
            1,
            "qr-code",
            "DISPONIBLE",
            "https://tiendasonograma.com/ventas/nueva?idDisco=708&qr=qr-code",
            "/api/qr/descargar/708/1"
        );

        when(discoRepository.findByDiscogsUrl(VinylFutureCqtr005DemoSeedService.PRODUCT_URL))
            .thenReturn(Optional.empty());
        when(discoRepository.findByCodigoInterno("CQTR005")).thenReturn(Optional.empty());
        when(scraperService.scrape(VinylFutureCqtr005DemoSeedService.PRODUCT_URL))
            .thenReturn(Optional.of(scraped));
        when(assetService.storeAssetsWithResult(any(), any()))
            .thenReturn(new VinylFutureAssetService.AssetStoreResult(stored, 1, 4, 0));
        when(discoService.crearDisco(any())).thenReturn(DiscoResponseDTO.builder().idDisco(708L).build());
        when(discoRepository.findById(708L)).thenReturn(Optional.of(saved));
        when(qrCopyService.totalCopies(708L)).thenReturn(1);
        when(qrCopyService.countAvailableCopies(708L)).thenReturn(1L);
        when(qrCopyService.listDtos(saved)).thenReturn(List.of(copy));
        when(qrService.descargarQR(708L, 1)).thenReturn(new byte[] {1, 2, 3});

        VinylFutureCqtr005DemoSeedService.SeedResult result = seedService.seed();

        ArgumentCaptor<DiscoRequestDTO> requestCaptor = ArgumentCaptor.forClass(DiscoRequestDTO.class);
        verify(discoService).crearDisco(requestCaptor.capture());
        DiscoRequestDTO request = requestCaptor.getValue();
        assertThat(request.getCodigoInterno()).isEqualTo("CQTR005");
        assertThat(request.getArtista()).isEqualTo("Various");
        assertThat(request.getAlbum()).isEqualTo("CQTR005");
        assertThat(request.getCosto()).isNull();
        assertThat(request.getNumeroFacturaCompra()).isNull();
        assertThat(request.getFechaFacturaCompra()).isNull();
        assertThat(request.getPrecioVenta()).isNull();
        assertThat(request.getCantidadCopias()).isEqualTo(1);
        assertThat(request.getProcedencia()).isEqualTo("Future");
        assertThat(request.getDiscogsUrl()).isEqualTo(VinylFutureCqtr005DemoSeedService.PRODUCT_URL);
        assertThat(request.getImagenUrl()).startsWith("/api/importar/vinylfuture/media/");
        assertThat(request.getTracklist()).contains("A1 SV3 - Armament Belico", "B2 Sebastian - Desapariciones");
        verify(audioPreviewService).guardarDesdeTracks(708L, stored.tracks());
        verify(qrService).descargarQR(708L, 1);
        assertThat(result.created()).isTrue();
        assertThat(result.discoId()).isEqualTo(708L);
        assertThat(result.copyId()).isEqualTo(901L);
        assertThat(result.qrPngBytes()).isEqualTo(3);
    }

    @Test
    void exactProductUrlMakesRerunANoOp() {
        Disco existing = Disco.builder()
            .idDisco(708L)
            .codigoInterno("CQTR005")
            .discogsUrl(VinylFutureCqtr005DemoSeedService.PRODUCT_URL)
            .build();
        DiscoQrCopyDTO copy = new DiscoQrCopyDTO(
            901L, 1, "qr-code", "DISPONIBLE", "content", "/api/qr/descargar/708/1"
        );
        when(discoRepository.findByDiscogsUrl(VinylFutureCqtr005DemoSeedService.PRODUCT_URL))
            .thenReturn(Optional.of(existing));
        when(qrCopyService.listDtos(existing)).thenReturn(List.of(copy));

        VinylFutureCqtr005DemoSeedService.SeedResult result = seedService.seed();

        assertThat(result.created()).isFalse();
        assertThat(result.discoId()).isEqualTo(708L);
        verify(scraperService, never()).scrape(any());
        verify(discoService, never()).crearDisco(any());
        verify(audioPreviewService, never()).guardarDesdeTracks(any(), any());
    }

    private VinylPageData page(String coverUrl, String firstAudioUrl) {
        return new VinylPageData(
            VinylFutureCqtr005DemoSeedService.PRODUCT_URL,
            "Various",
            "CQTR005",
            "CQTR005",
            "Coqueto Records",
            "House Electro Acid Techno",
            2025,
            null,
            "12inch Vinyl",
            null,
            "The next episode of Coqueto Records returns to the VA format.",
            null,
            coverUrl,
            null,
            List.of(
                new TrackInfo("A1", "SV3 - Armament Belico", firstAudioUrl, null),
                new TrackInfo("A2", "TC80 - Hipnosis Global", firstAudioUrl + "2", null),
                new TrackInfo("B1", "Trajano - Sistema Cabuloso", firstAudioUrl + "3", null),
                new TrackInfo("B2", "Sebastian - Desapariciones", firstAudioUrl + "4", null)
            )
        );
    }
}
