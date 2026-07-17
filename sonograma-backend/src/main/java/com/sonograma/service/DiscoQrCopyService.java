package com.sonograma.service;

import com.sonograma.dto.DiscoQrCopyDTO;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.exception.NegocioException;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.DiscoQrCopyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DiscoQrCopyService {

    private final DiscoQrCopyRepository repository;

    @Value("${sonograma.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    public List<DiscoQrCopy> synchronize(Disco disco) {
        return synchronizeAvailableCopies(disco, disco.getCantidadCopias() == null ? 0 : disco.getCantidadCopias());
    }

    public List<DiscoQrCopy> synchronizeAvailableCopies(Disco disco, int desiredAvailableCopies) {
        if (disco.getIdDisco() == null) {
            throw new IllegalArgumentException("El disco debe estar guardado antes de generar sus QR");
        }

        int target = Math.max(0, desiredAvailableCopies);
        List<DiscoQrCopy> current = new ArrayList<>(
            repository.findByIdDiscoOrderByCopyNumber(disco.getIdDisco())
        );
        List<DiscoQrCopy> available = current.stream()
            .filter(copy -> copy.getEstado() == EstadoCopiaDisco.DISPONIBLE)
            .sorted(Comparator.comparing(DiscoQrCopy::getCopyNumber))
            .collect(Collectors.toCollection(ArrayList::new));

        if (current.isEmpty() && target > 0 && disco.getCodigoQr() != null && !disco.getCodigoQr().isBlank()) {
            DiscoQrCopy created = repository.save(DiscoQrCopy.builder()
                .idDisco(disco.getIdDisco())
                .copyNumber(1)
                .codigoQr(disco.getCodigoQr())
                .estado(EstadoCopiaDisco.DISPONIBLE)
                .build());
            current.add(created);
            available.add(created);
        }

        while (available.size() < target) {
            DiscoQrCopy created = repository.save(DiscoQrCopy.builder()
                .idDisco(disco.getIdDisco())
                .copyNumber(nextCopyNumber(current))
                .codigoQr(UUID.randomUUID().toString())
                .estado(EstadoCopiaDisco.DISPONIBLE)
                .build());
            current.add(created);
            available.add(created);
        }

        if (available.size() > target) {
            List<DiscoQrCopy> removed = new ArrayList<>(available.subList(target, available.size()));
            repository.deleteAll(removed);
            current.removeIf(copy -> removed.stream().anyMatch(candidate -> Objects.equals(candidate.getId(), copy.getId())));
        }

        List<DiscoQrCopy> fresh = repository.findByIdDiscoOrderByCopyNumber(disco.getIdDisco());
        disco.setCantidadCopias((int) fresh.stream().filter(copy -> copy.getEstado() == EstadoCopiaDisco.DISPONIBLE).count());
        if (!fresh.isEmpty()) {
            disco.setCodigoQr(fresh.stream()
                .filter(copy -> copy.getEstado() == EstadoCopiaDisco.DISPONIBLE)
                .findFirst()
                .or(() -> fresh.stream().findFirst())
                .map(DiscoQrCopy::getCodigoQr)
                .orElse(null));
        } else {
            disco.setCodigoQr(null);
        }
        return fresh;
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

    @Transactional(readOnly = true)
    public long countAvailableCopies(Long discoId) {
        return repository.countByIdDiscoAndEstado(discoId, EstadoCopiaDisco.DISPONIBLE);
    }

    @Transactional(readOnly = true)
    public int totalCopies(Long discoId) {
        return repository.findByIdDiscoOrderByCopyNumber(discoId).size();
    }

    @Transactional(readOnly = true)
    public int soldCopies(Long discoId) {
        return (int) repository.countByIdDiscoAndEstado(discoId, EstadoCopiaDisco.VENDIDO);
    }

    public List<DiscoQrCopy> reserveCopies(Disco disco, int quantity, Long requestedCopyId, String requestedQr) {
        List<DiscoQrCopy> available = repository.findByIdDiscoAndEstadoOrderByCopyNumber(disco.getIdDisco(), EstadoCopiaDisco.DISPONIBLE);
        if (requestedCopyId != null || (requestedQr != null && !requestedQr.isBlank())) {
            if (quantity != 1) {
                throw new NegocioException("Una copia específica solo puede venderse de a una unidad");
            }
            DiscoQrCopy requested = requestedCopyId != null
                ? available.stream().filter(copy -> Objects.equals(copy.getId(), requestedCopyId)).findFirst().orElse(null)
                : available.stream().filter(copy -> requestedQr.equals(copy.getCodigoQr())).findFirst().orElse(null);
            if (requested == null) {
                throw new NegocioException("La copia escaneada ya no está disponible");
            }
            requested.setEstado(EstadoCopiaDisco.VENDIDO);
            repository.save(requested);
            return List.of(requested);
        }
        if (available.size() < quantity) {
            throw new NegocioException("No hay suficientes copias disponibles para esa venta");
        }
        List<DiscoQrCopy> reserved = new ArrayList<>(available.subList(0, quantity));
        reserved.forEach(copy -> copy.setEstado(EstadoCopiaDisco.VENDIDO));
        return repository.saveAll(reserved);
    }

    public void marcarDisponiblesVendidas(Disco disco) {
        List<DiscoQrCopy> disponibles = repository.findByIdDiscoAndEstadoOrderByCopyNumber(
            disco.getIdDisco(), EstadoCopiaDisco.DISPONIBLE);
        disponibles.forEach(copy -> copy.setEstado(EstadoCopiaDisco.VENDIDO));
        repository.saveAll(disponibles);
    }

    public void restoreCopies(String copyIdsSnapshot) {
        if (copyIdsSnapshot == null || copyIdsSnapshot.isBlank()) {
            return;
        }
        List<Long> ids = java.util.Arrays.stream(copyIdsSnapshot.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .map(Long::valueOf)
            .toList();
        if (ids.isEmpty()) {
            return;
        }
        List<DiscoQrCopy> copies = repository.findAllById(ids);
        copies.forEach(copy -> copy.setEstado(EstadoCopiaDisco.DISPONIBLE));
        repository.saveAll(copies);
    }

    public DiscoQrCopyDTO changeCopyStatus(Disco disco, Long copyId, EstadoCopiaDisco newState) {
        DiscoQrCopy copy = repository.findById(copyId)
            .filter(candidate -> Objects.equals(candidate.getIdDisco(), disco.getIdDisco()))
            .orElseThrow(() -> new RecursoNoEncontradoException("Copia", copyId));
        copy.setEstado(newState);
        return toDto(disco, repository.save(copy));
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
            copy.getEstado().name(),
            content(disco, copy),
            "/api/qr/descargar/" + disco.getIdDisco() + "/" + copy.getCopyNumber()
        );
    }

    private int nextCopyNumber(List<DiscoQrCopy> current) {
        return current.stream()
            .map(DiscoQrCopy::getCopyNumber)
            .filter(Objects::nonNull)
            .max(Integer::compareTo)
            .orElse(0) + 1;
    }
}
