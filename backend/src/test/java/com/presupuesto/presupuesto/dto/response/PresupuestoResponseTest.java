package com.presupuesto.presupuesto.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.presupuesto.entity.Presupuesto;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PresupuestoResponseTest {

    @Test
    void desdeCopiaCadaCampoDelPresupuesto() {
        Instant creado = Instant.parse("2026-10-02T12:00:00Z");
        Instant actualizado = Instant.parse("2026-10-03T12:00:00Z");
        Presupuesto presupuesto = Presupuesto.builder()
                .id(7L)
                .nombre("Casa")
                .nombreNormalizado("casa")
                .moneda("USD")
                .fechaCreacion(creado)
                .fechaActualizacion(actualizado)
                .build();

        PresupuestoResponse response = PresupuestoResponse.desde(presupuesto);

        assertThat(response)
                .isEqualTo(new PresupuestoResponse(7L, "Casa", "USD", creado, actualizado));
    }
}
