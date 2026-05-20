package com.sonograma.service;

import com.sonograma.dto.DiscoDTO;
import com.sonograma.entity.Disco;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.util.QRCodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QRService {

    private final DiscoRepository discoRepository;
    private final QRCodeGenerator qrCodeGenerator;
    private final DiscoService discoService;

    public byte[] descargarQR(Long idDisco) {
        Disco disco = discoRepository.findById(idDisco)
                .orElseThrow(() -> new IllegalArgumentException("Disco no encontrado: " + idDisco));

        if (disco.getCodigoQr() == null) {
            throw new IllegalArgumentException("El disco no tiene código QR generado");
        }

        return qrCodeGenerator.generarQRBytes(disco.getCodigoQr());
    }

    public DiscoDTO obtenerPorQRScaneado(String codigoQr) {
        return discoRepository.findByCodigoQr(codigoQr)
                .map(discoService::mapearADTO)
                .orElseThrow(() -> new IllegalArgumentException("QR no válido o disco no encontrado"));
    }
}
