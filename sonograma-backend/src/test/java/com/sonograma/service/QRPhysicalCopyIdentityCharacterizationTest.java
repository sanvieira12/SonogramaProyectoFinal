package com.sonograma.service;

import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.repository.DiscoQrCopyRepository;
import com.sonograma.repository.DiscoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class QRPhysicalCopyIdentityCharacterizationTest {

    @Autowired private QRService qrService;
    @Autowired private DiscoQrCopyService copyService;
    @Autowired private DiscoRepository discoRepository;
    @Autowired private DiscoQrCopyRepository copyRepository;

    @Test
    void eachQrResolvesItsOwnPhysicalCopyBeforeReturningTheSharedProduct() {
        Disco product = discoRepository.saveAndFlush(Disco.builder()
                .artista("QR Characterization")
                .album("Two Physical Copies")
                .codigoQr("legacy-qr-characterization")
                .cantidadCopias(2)
                .estado(EstadoDisco.DISPONIBLE)
                .build());
        DiscoQrCopy copyA = copyRepository.saveAndFlush(DiscoQrCopy.builder()
                .idDisco(product.getIdDisco())
                .copyNumber(1)
                .codigoQr("phase0-exact-qr-a")
                .estado(EstadoCopiaDisco.DISPONIBLE)
                .build());
        DiscoQrCopy copyB = copyRepository.saveAndFlush(DiscoQrCopy.builder()
                .idDisco(product.getIdDisco())
                .copyNumber(2)
                .codigoQr("phase0-exact-qr-b")
                .estado(EstadoCopiaDisco.DISPONIBLE)
                .build());

        assertThat(copyService.findByCode("phase0-exact-qr-a")).extracting(DiscoQrCopy::getId)
                .isEqualTo(copyA.getId());
        assertThat(copyService.findByCode("phase0-exact-qr-b")).extracting(DiscoQrCopy::getId)
                .isEqualTo(copyB.getId());
        assertThat(copyA.getCodigoQr()).isNotEqualTo(copyB.getCodigoQr());

        // The current QR API returns the shared product DTO, while its internal lookup still starts
        // from the exact physical copy. Copy-specific response enrichment belongs to a later phase.
        assertThat(qrService.obtenerPorQRScaneado(copyA.getCodigoQr()).getIdDisco())
                .isEqualTo(product.getIdDisco());
        assertThat(qrService.obtenerPorQRScaneado(copyB.getCodigoQr()).getIdDisco())
                .isEqualTo(product.getIdDisco());
        assertThat(copyRepository.findByCodigoQr(copyA.getCodigoQr())).get()
                .extracting(DiscoQrCopy::getId).isNotEqualTo(copyB.getId());
    }
}
