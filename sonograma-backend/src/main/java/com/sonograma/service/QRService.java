package com.sonograma.service;

import com.sonograma.dto.DiscoResponseDTO;
import com.sonograma.entity.Disco;
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

    public byte[] descargarQR(Long idDisco) {
        Disco disco = discoRepository.findById(idDisco)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", idDisco));

        if (disco.getCodigoQr() == null) {
            throw new RecursoNoEncontradoException("El disco no tiene código QR generado");
        }

        return qrCodeGenerator.generarQRBytes(disco.getCodigoQr());
    }

    public DiscoResponseDTO obtenerPorQRScaneado(String codigoQr) {
        return discoRepository.findByCodigoQr(codigoQr)
                .map(DiscoMapper::toDTO)
                .orElseThrow(() -> new RecursoNoEncontradoException("QR no válido o disco no encontrado"));
    }
}
