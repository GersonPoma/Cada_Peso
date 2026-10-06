package com.presupuesto.cuenta.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class TipoCuentaTest {

    @Test
    void soloLasCuentasDeDeudaAdmitenSaldoNegativo() {
        assertThat(Arrays.stream(TipoCuenta.values()).filter(TipoCuenta::admiteSaldoNegativo))
                .containsExactlyInAnyOrder(TipoCuenta.TARJETA_CREDITO, TipoCuenta.PRESTAMO);
    }

    @Test
    void estanLosSeisTipos() {
        assertThat(TipoCuenta.values()).containsExactly(
                TipoCuenta.CORRIENTE,
                TipoCuenta.AHORRO,
                TipoCuenta.EFECTIVO,
                TipoCuenta.TARJETA_CREDITO,
                TipoCuenta.INVERSION,
                TipoCuenta.PRESTAMO);
    }
}
