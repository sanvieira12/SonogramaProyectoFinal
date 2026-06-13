package com.sonograma.service;

import com.sonograma.dto.DiscoQrCopyDTO;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.repository.DiscoQrCopyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DiscoQrCopyService {

    private final DiscoQrCopyRepository repository;

    @Value("${sonograma.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    public List<DiscoQrCopy> synchronize(Disco disco) {
        if (disco.getIdDisco() == null) {
            throw new IllegalArgumentException("El disco debe estar guardado antes de generar sus QR");
        }

        int target = Math.max(0, disco.getCantidadCopias() == null ? 1 : disco.getCantidadCopias());
        List<DiscoQrCopy> current = new ArrayList<>(
            repository.findByIdDiscoOrderByCopyNumber(disco.getIdDisco())
        );

        if (current.isEmpty() && target > 0 && disco.getCodigoQr() != null && !disco.getCodigoQr().isBlank()) {
            current.add(repository.save(DiscoQrCopy.builder()
                .idDisco(disco.getIdDisco())
                .copyNumber(1)
                .codigoQr(disco.getCodigoQr())
                .build()));
        }

        while (current.size() < target) {
            current.add(repository.save(DiscoQrCopy.builder()
                .idDisco(disco.getIdDisco())
                .copyNumber(current.size() + 1)
                .codigoQr(UUID.randomUUID().toString())
                .build()));
        }

        if (current.size() > target) {
            List<DiscoQrCopy> removed = new ArrayList<>(current.subList(target, current.size()));
            repository.deleteAll(removed);
            current = new ArrayList<>(current.subList(0, target));
        }

        if (!current.isEmpty()) {
            disco.setCodigoQr(current.get(0).getCodigoQr());
        } else {
            disco.setCodigoQr(null);
        }
        return current;
    }

    @Transactional(readOnly = true)
    public List<DiscoQrCopyDTO> listDtos(Disco disco) {
        return repository.findByIdDiscoOrderByCopyNumber(disco.getIdDisco()).stream()
            .map(copy -> toDto(disco, copy))
            .toList();
    }

    @Transactional(readOnly = true)
    public DiscoQrCopy findByCode(String code) {
        return repository.findByCodigoQr(code).orElse(null);
    }

    public String content(Disco disco, DiscoQrCopy copy) {
        return frontendBaseUrl.replaceAll("/+$", "")
            + "/ventas/nueva?idDisco=" + disco.getIdDisco()
            + "&qr=" + copy.getCodigoQr();
    }

    private DiscoQrCopyDTO toDto(Disco disco, DiscoQrCopy copy) {
        return new DiscoQrCopyDTO(
            copy.getId(),
            copy.getCopyNumber(),
            copy.getCodigoQr(),
            content(disco, copy),
            "/api/qr/descargar/" + disco.getIdDisco() + "/" + copy.getCopyNumber()
        );
    }
}
