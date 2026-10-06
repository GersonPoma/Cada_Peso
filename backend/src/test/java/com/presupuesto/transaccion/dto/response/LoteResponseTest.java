package com.presupuesto.transaccion.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoteResponseTest {

    @Test
    void guardaLaCantidadAfectada() {
        assertThat(new LoteResponse(3).afectadas()).isEqualTo(3);
    }
}
