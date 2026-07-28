package com.sonograma.service;

import com.sonograma.dto.CotizacionEnvioDTO;
import com.sonograma.dto.SucursalDacDTO;
import com.sonograma.exception.NegocioException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class DacService {

    public List<String> obtenerDepartamentos() {
        return DacBranchCatalog.DEPARTAMENTOS;
    }

    public List<SucursalDacDTO> obtenerSucursales(String departamento) {
        return DacBranchCatalog.getByDepartment(departamento).stream()
                .map(DacService::toDTO)
                .toList();
    }

    public CotizacionEnvioDTO cotizar(String departamento, String sucursalCodigo) {
        SucursalDacDTO sucursal = DacBranchCatalog.findById(sucursalCodigo)
                .filter(branch -> DacBranchCatalog.belongsToDepartment(branch.id(), departamento))
                .map(DacService::toDTO)
                .orElseThrow(() -> new NegocioException("La sucursal DAC no pertenece al departamento seleccionado"));

        BigDecimal costo = calcularCostoBase(departamento);
        return CotizacionEnvioDTO.builder()
                .proveedor("DAC")
                .departamento(departamento)
                .sucursalCodigo(sucursalCodigo)
                .sucursalNombre(sucursal != null ? sucursal.getNombre() : null)
                .costoEstimado(costo)
                .moneda("UYU")
                .build();
    }

    // TODO: reemplazar este mock por un cliente HTTP si DAC publica una API estable para sucursales y tarifas.
    private BigDecimal calcularCostoBase(String departamento) {
        if ("Montevideo".equalsIgnoreCase(departamento)) return new BigDecimal("220.00");
        if ("Canelones".equalsIgnoreCase(departamento)) return new BigDecimal("260.00");
        return new BigDecimal("330.00");
    }

    public Optional<DacBranchCatalog.Branch> findById(String id) {
        return DacBranchCatalog.findById(id);
    }

    public boolean belongsToDepartment(String id, String departamento) {
        return DacBranchCatalog.belongsToDepartment(id, departamento);
    }

    private static SucursalDacDTO toDTO(DacBranchCatalog.Branch branch) {
        return SucursalDacDTO.builder()
                .id(branch.id())
                .codigo(branch.codigo())
                .nombre(branch.nombre())
                .departamento(branch.departamento())
                .direccion(branch.direccion())
                .label(branch.label())
                .build();
    }
}
