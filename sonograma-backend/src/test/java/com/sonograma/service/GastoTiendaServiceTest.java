package com.sonograma.service;

import com.sonograma.dto.GastoTiendaRequestDTO;
import com.sonograma.entity.GastoTienda;
import com.sonograma.enums.CategoriaGasto;
import com.sonograma.repository.GastoTiendaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GastoTiendaServiceTest {

    private GastoTiendaRepository repository;
    private GastoTiendaService service;

    @BeforeEach
    void setUp() {
        repository = mock(GastoTiendaRepository.class);
        service = new GastoTiendaService(repository, new BusinessTime(Clock.systemUTC()));
        when(repository.save(any(GastoTienda.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void creaUnGastoPersistiendoLaCategoria() {
        var result = service.crear(request(CategoriaGasto.NEW_ORDERS));

        assertThat(result.getCategoria()).isEqualTo(CategoriaGasto.NEW_ORDERS);
        var saved = org.mockito.ArgumentCaptor.forClass(GastoTienda.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getCategoria()).isEqualTo(CategoriaGasto.NEW_ORDERS);
    }

    @Test
    void creaLeeYActualizaUnGastoPersonal() {
        var created = service.crear(request(CategoriaGasto.PERSONAL_EXPENSES));
        assertThat(created.getCategoria()).isEqualTo(CategoriaGasto.PERSONAL_EXPENSES);

        var existing = GastoTienda.builder().idGasto(9L).fecha(LocalDate.of(2026, 7, 1))
                .descripcion("Nafta").monto(new BigDecimal("3000"))
                .categoria(CategoriaGasto.PERSONAL_EXPENSES).build();
        when(repository.findById(9L)).thenReturn(Optional.of(existing));

        var updated = service.actualizar(9L, request(CategoriaGasto.PERSONAL_EXPENSES));

        assertThat(updated.getCategoria()).isEqualTo(CategoriaGasto.PERSONAL_EXPENSES);
        when(repository.findAllByOrderByFechaDescIdGastoDesc()).thenReturn(java.util.List.of(existing));
        assertThat(service.listar()).singleElement()
                .extracting(resultDto -> resultDto.getCategoria())
                .isEqualTo(CategoriaGasto.PERSONAL_EXPENSES);
    }

    @Test
    void actualizaLaCategoria() {
        var existing = GastoTienda.builder().idGasto(7L).fecha(LocalDate.now())
                .descripcion("Compra").monto(new BigDecimal("100")).categoria(CategoriaGasto.USED_ORDERS).build();
        when(repository.findById(7L)).thenReturn(Optional.of(existing));

        var result = service.actualizar(7L, request(CategoriaGasto.STORE_EXPENSES));

        assertThat(result.getCategoria()).isEqualTo(CategoriaGasto.STORE_EXPENSES);
    }

    @Test
    void mantieneHistoricosSinCategoria() {
        var historical = GastoTienda.builder().idGasto(8L).fecha(LocalDate.now())
                .descripcion("Histórico").monto(new BigDecimal("50")).categoria(null).build();
        when(repository.findAllByOrderByFechaDescIdGastoDesc()).thenReturn(java.util.List.of(historical));

        assertThat(service.listar().getFirst().getCategoria()).isNull();
    }

    private GastoTiendaRequestDTO request(CategoriaGasto categoria) {
        return GastoTiendaRequestDTO.builder().fecha(LocalDate.of(2026, 7, 1))
                .descripcion("Compra").monto(new BigDecimal("100")).categoria(categoria).build();
    }
}
