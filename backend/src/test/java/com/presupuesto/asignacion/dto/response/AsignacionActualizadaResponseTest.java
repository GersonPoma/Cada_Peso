package com.presupuesto.asignacion.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AsignacionActualizadaResponseTest {

    @Test
    void guardaLaFilaCalculadaYElListoParaAsignar() {
        CategoriaMesResponse fila =
                new CategoriaMesResponse(1L, "Comida", false, 100_000L, 0L, 100_000L, false);

        AsignacionActualizadaResponse respuesta =
                AsignacionActualizadaResponse.desde(fila, 400_000L);

        assertThat(respuesta.categoria()).isSameAs(fila);
        assertThat(respuesta.listoParaAsignar()).isEqualTo(400_000L);
    }
}
