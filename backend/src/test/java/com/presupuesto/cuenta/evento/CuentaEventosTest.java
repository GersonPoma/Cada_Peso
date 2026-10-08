package com.presupuesto.cuenta.evento;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.cuenta.entity.Cuenta;
import org.junit.jupiter.api.Test;

class CuentaEventosTest {

    @Test
    void cadaEventoLlevaLaCuentaYaGuardada() {
        Cuenta cuenta = Cuenta.builder().id(5L).nombre("Visa").build();

        assertThat(new CuentaCreadaEvento(cuenta).cuenta()).isSameAs(cuenta);
        assertThat(new CuentaRenombradaEvento(cuenta).cuenta()).isSameAs(cuenta);
        assertThat(new CuentaCerradaEvento(cuenta).cuenta()).isSameAs(cuenta);
        assertThat(new CuentaReabiertaEvento(cuenta).cuenta()).isSameAs(cuenta);
    }
}
