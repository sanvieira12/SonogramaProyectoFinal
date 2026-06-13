package com.sonograma.service;

import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.repository.DiscoQrCopyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscoQrCopyServiceTest {

    @Mock
    private DiscoQrCopyRepository repository;

    private DiscoQrCopyService service;

    @BeforeEach
    void setUp() {
        service = new DiscoQrCopyService(repository);
    }

    @Test
    void synchronizeCreatesOneUniqueQrPerPhysicalCopy() {
        Disco disco = Disco.builder()
            .idDisco(42L)
            .cantidadCopias(2)
            .codigoQr("existing-first-code")
            .build();
        List<DiscoQrCopy> stored = new ArrayList<>();

        when(repository.findByIdDiscoOrderByCopyNumber(42L)).thenReturn(List.of());
        when(repository.save(any(DiscoQrCopy.class))).thenAnswer(invocation -> {
            DiscoQrCopy copy = invocation.getArgument(0);
            copy.setId((long) stored.size() + 1);
            stored.add(copy);
            return copy;
        });

        List<DiscoQrCopy> result = service.synchronize(disco);

        assertEquals(2, result.size());
        assertEquals("existing-first-code", result.get(0).getCodigoQr());
        assertNotEquals(result.get(0).getCodigoQr(), result.get(1).getCodigoQr());
        assertEquals(List.of(1, 2), result.stream().map(DiscoQrCopy::getCopyNumber).toList());
        verify(repository, times(2)).save(any(DiscoQrCopy.class));
    }

    @Test
    void synchronizeRemovesQrEntriesWhenStockShrinks() {
        Disco disco = Disco.builder().idDisco(7L).cantidadCopias(1).build();
        List<DiscoQrCopy> current = List.of(
            DiscoQrCopy.builder().id(1L).idDisco(7L).copyNumber(1).codigoQr("one").build(),
            DiscoQrCopy.builder().id(2L).idDisco(7L).copyNumber(2).codigoQr("two").build()
        );
        when(repository.findByIdDiscoOrderByCopyNumber(7L)).thenReturn(current);

        List<DiscoQrCopy> result = service.synchronize(disco);

        assertEquals(1, result.size());
        assertEquals("one", disco.getCodigoQr());
        verify(repository).deleteAll(List.of(current.get(1)));
    }
}
