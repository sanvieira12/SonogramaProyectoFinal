package com.sonograma.service;

import com.sonograma.dto.GastoTiendaDTO;
import com.sonograma.dto.GastoTiendaResumenDTO;
import com.sonograma.entity.GastoTienda;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.GastoTiendaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class GastoTiendaService {

    private final GastoTiendaRepository repository;

    @Transactional(readOnly = true)
    public List<GastoTiendaDTO> listar() {
        return repository.findAllByOrderByFechaDescIdGastoDesc().stream().map(this::toDto).toList();
    }

    public GastoTiendaDTO crear(GastoTiendaDTO request) {
        GastoTienda gasto = GastoTienda.builder()
            .fecha(request.getFecha() != null ? request.getFecha() : LocalDate.now())
            .descripcion(request.getDescripcion())
            .monto(request.getMonto())
            .build();
        return toDto(repository.save(gasto));
    }

    public GastoTiendaDTO actualizar(Long id, GastoTiendaDTO request) {
        GastoTienda gasto = repository.findById(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("Gasto", id));
        if (request.getFecha() != null) gasto.setFecha(request.getFecha());
        if (request.getDescripcion() != null) gasto.setDescripcion(request.getDescripcion());
        if (request.getMonto() != null) gasto.setMonto(request.getMonto());
        return toDto(repository.save(gasto));
    }

    public void eliminar(Long id) {
        if (!repository.existsById(id)) {
            throw new RecursoNoEncontradoException("Gasto", id);
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public GastoTiendaResumenDTO resumenMesActual() {
        LocalDate now = LocalDate.now();
        LocalDate desde = now.withDayOfMonth(1);
        LocalDate hasta = now.withDayOfMonth(now.lengthOfMonth());
        BigDecimal total = repository.findByFechaBetween(desde, hasta).stream()
            .map(GastoTienda::getMonto)
            .filter(v -> v != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new GastoTiendaResumenDTO(total);
    }

    private GastoTiendaDTO toDto(GastoTienda gasto) {
        return GastoTiendaDTO.builder()
            .idGasto(gasto.getIdGasto())
            .fecha(gasto.getFecha())
            .descripcion(gasto.getDescripcion())
            .monto(gasto.getMonto())
            .build();
    }
}
