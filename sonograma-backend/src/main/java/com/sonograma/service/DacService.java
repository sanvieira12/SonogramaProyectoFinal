package com.sonograma.service;

import com.sonograma.dto.CotizacionEnvioDTO;
import com.sonograma.dto.SucursalDacDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class DacService {

    private static final Map<String, List<SucursalDacDTO>> SUCURSALES = Map.ofEntries(
            Map.entry("Montevideo", List.of(
                    sucursal("MVD-CEN", "DAC Centro", "Montevideo", "18 de Julio 1234"),
                    sucursal("MVD-TRE", "DAC Tres Cruces", "Montevideo", "Bvar. Artigas 1825")
            )),
            Map.entry("Canelones", List.of(sucursal("CAN-CAN", "DAC Canelones", "Canelones", "Treinta y Tres 470"))),
            Map.entry("Maldonado", List.of(sucursal("MAL-PDE", "DAC Punta del Este", "Maldonado", "Av. Gorlero 890"))),
            Map.entry("Colonia", List.of(sucursal("COL-COL", "DAC Colonia", "Colonia", "Gral. Flores 340"))),
            Map.entry("Salto", List.of(sucursal("SAL-SAL", "DAC Salto", "Salto", "Uruguay 920"))),
            Map.entry("Paysandú", List.of(sucursal("PAY-PAY", "DAC Paysandú", "Paysandú", "18 de Julio 1110"))),
            Map.entry("Rivera", List.of(sucursal("RIV-RIV", "DAC Rivera", "Rivera", "Sarandí 620"))),
            Map.entry("Rocha", List.of(sucursal("ROC-ROC", "DAC Rocha", "Rocha", "25 de Agosto 170"))),
            Map.entry("San José", List.of(sucursal("SJO-SJO", "DAC San José", "San José", "Artigas 800"))),
            Map.entry("Soriano", List.of(sucursal("SOR-MER", "DAC Mercedes", "Soriano", "Colón 540"))),
            Map.entry("Tacuarembó", List.of(sucursal("TAC-TAC", "DAC Tacuarembó", "Tacuarembó", "18 de Julio 290"))),
            Map.entry("Durazno", List.of(sucursal("DUR-DUR", "DAC Durazno", "Durazno", "Brig. Gral. Fructuoso Rivera 510"))),
            Map.entry("Florida", List.of(sucursal("FLO-FLO", "DAC Florida", "Florida", "Independencia 610"))),
            Map.entry("Lavalleja", List.of(sucursal("LAV-MIN", "DAC Minas", "Lavalleja", "Treinta y Tres 220"))),
            Map.entry("Cerro Largo", List.of(sucursal("CEL-MEL", "DAC Melo", "Cerro Largo", "Aparicio Saravia 710"))),
            Map.entry("Artigas", List.of(sucursal("ART-ART", "DAC Artigas", "Artigas", "Lecueder 950"))),
            Map.entry("Flores", List.of(sucursal("FLS-TRI", "DAC Trinidad", "Flores", "Francisco Fondar 640"))),
            Map.entry("Río Negro", List.of(sucursal("RNE-FBE", "DAC Fray Bentos", "Río Negro", "18 de Julio 1250"))),
            Map.entry("Treinta y Tres", List.of(sucursal("TTY-TTY", "DAC Treinta y Tres", "Treinta y Tres", "Manuel Lavalleja 85")))
    );

    public List<String> obtenerDepartamentos() {
        return SUCURSALES.keySet().stream().sorted().toList();
    }

    public List<SucursalDacDTO> obtenerSucursales(String departamento) {
        return SUCURSALES.getOrDefault(departamento, List.of()).stream()
                .sorted(Comparator.comparing(SucursalDacDTO::getNombre))
                .toList();
    }

    public CotizacionEnvioDTO cotizar(String departamento, String sucursalCodigo) {
        SucursalDacDTO sucursal = obtenerSucursales(departamento).stream()
                .filter(s -> s.getCodigo().equals(sucursalCodigo))
                .findFirst()
                .orElse(null);

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

    private static SucursalDacDTO sucursal(String codigo, String nombre, String departamento, String direccion) {
        return SucursalDacDTO.builder()
                .codigo(codigo)
                .nombre(nombre)
                .departamento(departamento)
                .direccion(direccion)
                .build();
    }
}
