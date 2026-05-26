package com.sonograma.service;

import com.sonograma.dto.ResultadoCostoVentaDTO;
import com.sonograma.dto.VentaRequestDTO;
import com.sonograma.entity.Disco;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CostosVentaServiceTest {

    @Test
    void calcularIncluyeEnvioImpuestosOtrosCostosYGanancia() {
        CostosVentaService service = new CostosVentaService();
        ReflectionTestUtils.setField(service, "porcentajeImpuestoDefault", new BigDecimal("10"));
        ReflectionTestUtils.setField(service, "otrosCostosDefault", new BigDecimal("50"));

        Disco disco = Disco.builder()
                .costo(new BigDecimal("400"))
                .precioVenta(new BigDecimal("1000"))
                .build();
        VentaRequestDTO request = VentaRequestDTO.builder()
                .tipoEntrega("ENVIO")
                .precioVenta(new BigDecimal("1000"))
                .costoEnvio(new BigDecimal("200"))
                .build();

        ResultadoCostoVentaDTO resultado = service.calcular(disco, request);

        assertThat(resultado.getMontoImpuesto()).isEqualByComparingTo("125.00");
        assertThat(resultado.getTotalFinal()).isEqualByComparingTo("1375.00");
        assertThat(resultado.getGananciaEstimada()).isEqualByComparingTo("600.00");
    }
}
