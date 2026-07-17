package com.sonograma.service;

import com.sonograma.entity.Disco;
import com.sonograma.enums.EstadoDisco;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Keeps the catalog status aligned with the persisted copy inventory. */
@Service
@RequiredArgsConstructor
public class DiscoEstadoService {

    private final DiscoQrCopyService discoQrCopyService;

    public EstadoDisco recalcular(Disco disco) {
        long disponibles = discoQrCopyService.countAvailableCopies(disco.getIdDisco());
        long vendidas = discoQrCopyService.soldCopies(disco.getIdDisco());

        if (disco.getEstado() == EstadoDisco.RESERVADO && disponibles > 0) {
            return EstadoDisco.RESERVADO;
        }
        if (disponibles > 0) {
            return EstadoDisco.DISPONIBLE;
        }
        return vendidas > 0 ? EstadoDisco.VENDIDO : EstadoDisco.SIN_STOCK;
    }

    public void aplicar(Disco disco) {
        long disponibles = discoQrCopyService.countAvailableCopies(disco.getIdDisco());
        long vendidas = discoQrCopyService.soldCopies(disco.getIdDisco());
        disco.setCantidadCopias((int) disponibles);
        if (disco.getEstado() == EstadoDisco.RESERVADO && disponibles > 0) {
            return;
        }
        if (disponibles > 0) {
            disco.setEstado(EstadoDisco.DISPONIBLE);
        } else {
            disco.setEstado(vendidas > 0 ? EstadoDisco.VENDIDO : EstadoDisco.SIN_STOCK);
        }
    }
}
