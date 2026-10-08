package com.presupuesto.transaccionprogramada.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.transaccionprogramada.service.ResultadoGeneracion;
import org.junit.jupiter.api.Test;

class GeneracionResponseTest {

    @Test
    void mapeaElResultadoDelGenerador() {
        GeneracionResponse respuesta = GeneracionResponse.desde(new ResultadoGeneracion(3, 1));

        assertThat(respuesta.generadas()).isEqualTo(3);
        assertThat(respuesta.plantillasConError()).isEqualTo(1);
    }
}
