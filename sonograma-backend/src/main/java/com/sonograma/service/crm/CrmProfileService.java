package com.sonograma.service.crm;

import com.sonograma.dto.crm.CrmDtos;
import com.sonograma.entity.Cliente;
import com.sonograma.entity.Venta;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.mapper.ClienteMapper;
import com.sonograma.repository.ClienteRepository;
import com.sonograma.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CrmProfileService {

    private final ClienteRepository clienteRepository;
    private final VentaRepository ventaRepository;
    private final CrmProfileCalculator calculator = new CrmProfileCalculator();

    public CrmDtos.PerfilCliente profileDto(Long customerId) {
        CrmProfileCalculator.CustomerProfile profile = profile(customerId);
        return new CrmDtos.PerfilCliente(
                ClienteMapper.toDTO(profile.cliente()), calculator.metricsDto(profile.metrics()),
                calculator.tasteDto(profile.historical()), calculator.tasteDto(profile.recent()),
                calculator.historyDto(profile.lines())
        );
    }

    public CrmProfileCalculator.CustomerProfile profile(Long customerId) {
        Cliente customer = activeCustomer(customerId);
        return calculate(customer, ventaRepository.findCompletedForCrmCustomer(customerId));
    }

    CrmProfileCalculator.CustomerProfile calculate(Cliente customer, List<Venta> sales) {
        return calculator.calculate(customer, sales, LocalDateTime.now());
    }

    public Cliente activeCustomer(Long customerId) {
        return clienteRepository.findById(customerId)
                .filter(Cliente::getActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", customerId));
    }
}
