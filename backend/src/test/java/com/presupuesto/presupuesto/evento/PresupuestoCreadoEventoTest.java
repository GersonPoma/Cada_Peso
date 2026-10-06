package com.presupuesto.presupuesto.evento;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.presupuesto.entity.Presupuesto;
import org.junit.jupiter.api.Test;

class PresupuestoCreadoEventoTest {

    @Test
    void llevaElPresupuestoRecienGuardado() {
        Presupuesto presupuesto = Presupuesto.builder().id(5L).nombre("Casa").build();

        PresupuestoCreadoEvento evento = new PresupuestoCreadoEvento(presupuesto);

        assertThat(evento.presupuesto()).isSameAs(presupuesto);
    }
}
