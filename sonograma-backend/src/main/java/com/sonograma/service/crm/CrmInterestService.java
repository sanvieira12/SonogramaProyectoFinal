package com.sonograma.service.crm;

import com.sonograma.dto.crm.CrmDtos;
import com.sonograma.entity.Cliente;
import com.sonograma.entity.CrmInteresCliente;
import com.sonograma.enums.TipoInteresCrm;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.CrmInteresClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CrmInterestService {

    private final CrmInteresClienteRepository repository;
    private final CrmProfileService profileService;

    @Transactional(readOnly = true)
    public List<CrmDtos.Interes> list(Long customerId) {
        profileService.activeCustomer(customerId);
        return repository.findByClienteIdClienteOrderByActivoDescFechaCreacionDesc(customerId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CrmInteresCliente> activeEntities(Long customerId) {
        return repository.findByClienteIdClienteAndActivoTrueOrderByFechaCreacionDesc(customerId);
    }

    public CrmDtos.Interes create(Long customerId, CrmDtos.InteresRequest request) {
        Cliente customer = profileService.activeCustomer(customerId);
        CrmInteresCliente interest = CrmInteresCliente.builder()
                .cliente(customer)
                .tipo(request.tipo() == null ? TipoInteresCrm.LIBRE : request.tipo())
                .texto(request.texto().trim().replaceAll("\\s+", " "))
                .build();
        return toDto(repository.save(interest));
    }

    public CrmDtos.Interes setActive(Long customerId, Long interestId, boolean active) {
        profileService.activeCustomer(customerId);
        CrmInteresCliente interest = repository.findOwned(customerId, interestId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Interés CRM", interestId));
        interest.setActivo(active);
        return toDto(repository.save(interest));
    }

    CrmDtos.Interes toDto(CrmInteresCliente interest) {
        return new CrmDtos.Interes(interest.getIdInteres(), interest.getCliente().getIdCliente(),
                interest.getTipo(), interest.getTexto(), interest.getActivo(), interest.getFechaCreacion());
    }
}
