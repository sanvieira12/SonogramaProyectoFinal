package com.sonograma.service;

import com.sonograma.dto.CotizacionEnvioDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DacServiceTest {

    @Test
    void cotizarDevuelveCostoYSucursalMockParaDepartamento() {
        DacService service = new DacService();

        CotizacionEnvioDTO cotizacion = service.cotizar("Montevideo", "MVD-CEN");

        assertThat(cotizacion.getProveedor()).isEqualTo("DAC");
        assertThat(cotizacion.getSucursalNombre()).isEqualTo("DAC Centro");
        assertThat(cotizacion.getCostoEstimado()).isEqualByComparingTo("220.00");
    }
}
