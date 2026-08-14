package com.sonograma.service.crm;

import com.sonograma.dto.crm.CrmDtos;
import com.sonograma.entity.Cliente;
import com.sonograma.entity.CrmInteresCliente;
import com.sonograma.enums.TipoInteresCrm;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.CrmInteresClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrmInterestServiceTest {

    @Mock CrmInteresClienteRepository repository;
    @Mock CrmProfileService profiles;

    private CrmInterestService service;
    private Cliente customer;

    @BeforeEach
    void setUp() {
        service = new CrmInterestService(repository, profiles);
        customer = new Cliente();
        customer.setIdCliente(7L);
        customer.setNombre("Cliente");
        customer.setActivo(true);
    }

    @Test
    void defaultsToFreeNormalizesWhitespaceAndCanDeactivateOwnedInterest() {
        when(profiles.activeCustomer(7L)).thenReturn(customer);
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            CrmInteresCliente interest = invocation.getArgument(0);
            interest.setIdInteres(3L);
            return interest;
        });

        CrmDtos.Interes created = service.create(7L, new CrmDtos.InteresRequest(null, "  años   90  "));
        assertThat(created.tipo()).isEqualTo(TipoInteresCrm.LIBRE);
        assertThat(created.texto()).isEqualTo("años 90");

        CrmInteresCliente entity = CrmInteresCliente.builder().idInteres(3L).cliente(customer)
                .tipo(TipoInteresCrm.LIBRE).texto("años 90").activo(true).build();
        when(repository.findOwned(7L, 3L)).thenReturn(Optional.of(entity));
        assertThat(service.setActive(7L, 3L, false).activo()).isFalse();
    }

    @Test
    void rejectsCrossCustomerInterestUpdateAsNotFound() {
        when(profiles.activeCustomer(7L)).thenReturn(customer);
        when(repository.findOwned(7L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setActive(7L, 99L, false))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
