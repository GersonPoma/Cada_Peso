package com.presupuesto.transaccion.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EstadoTransaccionTest {

    @Test
    void tieneLosTresEstadosEnOrden() {
        assertThat(EstadoTransaccion.values())
                .containsExactly(
                        EstadoTransaccion.NO_CONCILIADA,
                        EstadoTransaccion.CONCILIADA,
                        EstadoTransaccion.RECONCILIADA);
    }
}
