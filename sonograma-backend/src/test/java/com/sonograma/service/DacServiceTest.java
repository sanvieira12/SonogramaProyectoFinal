package com.sonograma.service;

import com.sonograma.dto.CotizacionEnvioDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DacServiceTest {

    @Test
    void cotizarDevuelveCostoYSucursalOficialParaDepartamento() {
        DacService service = new DacService();

        CotizacionEnvioDTO cotizacion = service.cotizar("Montevideo", "dac-661");

        assertThat(cotizacion.getProveedor()).isEqualTo("DAC");
        assertThat(cotizacion.getSucursalNombre()).isEqualTo("Tres Cruces");
        assertThat(cotizacion.getCostoEstimado()).isEqualByComparingTo("220.00");
    }

    @Test
    void catalogoTieneLasAgenciasOficialesYOrdenaPorNombre() {
        DacService service = new DacService();

        assertThat(service.obtenerDepartamentos()).hasSize(19);
        assertThat(service.obtenerSucursales("Montevideo")).hasSize(14);
        assertThat(service.obtenerSucursales("Montevideo").get(0).getNombre()).isEqualTo("Agencia Perimetral Ruta 5");
        assertThat(service.obtenerSucursales("Rocha")).anyMatch(s -> s.getNombre().equals("La Paloma Rocha"));
        assertThat(service.obtenerSucursales("Durazno")).anyMatch(s -> s.getNombre().contains("La Paloma"));
        assertThat(service.belongsToDepartment("dac-661", "Montevideo")).isTrue();
        assertThat(service.belongsToDepartment("dac-661", "Rocha")).isFalse();
    }
}
