package com.sonograma.service;

import com.sonograma.dto.DiscoResponseDTO;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.mapper.DiscoMapper;
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
    private final DiscoQrCopyService qrCopyService;

    @Transactional
    public byte[] descargarQR(Long idDisco) {
        return descargarQR(idDisco, 1);
    }

    @Transactional
    public byte[] descargarQR(Long idDisco, Integer copyNumber) {
        Disco disco = discoRepository.findById(idDisco)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", idDisco));
        DiscoQrCopy copy = qrCopyService.synchronize(disco).stream()
            .filter(candidate -> candidate.getCopyNumber().equals(copyNumber))
            .findFirst()
            .orElseThrow(() -> new RecursoNoEncontradoException("Copia QR", copyNumber.longValue()));
        return qrCodeGenerator.generarQRBytes(qrCopyService.content(disco, copy));
    }

    public DiscoResponseDTO obtenerPorQRScaneado(String codigoQr) {
        DiscoQrCopy copy = qrCopyService.findByCode(codigoQr);
        if (copy != null) {
            return discoRepository.findById(copy.getIdDisco())
                .map(DiscoMapper::toDTO)
                .orElseThrow(() -> new RecursoNoEncontradoException("QR no válido o disco no encontrado"));
        }
        return discoRepository.findByCodigoQr(codigoQr)
                .map(DiscoMapper::toDTO)
                .orElseThrow(() -> new RecursoNoEncontradoException("QR no válido o disco no encontrado"));
    }
}
