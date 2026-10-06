package com.presupuesto.transaccion.validacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MontoNoCeroValidatorTest {

    private final MontoNoCeroValidator validador = new MontoNoCeroValidator();

    @Test
    void ceroEsInvalido() {
        assertThat(validador.isValid(0L, null)).isFalse();
    }

    @Test
    void positivosYNegativosSonValidos() {
        assertThat(validador.isValid(1L, null)).isTrue();
        assertThat(validador.isValid(-1L, null)).isTrue();
        assertThat(validador.isValid(Long.MIN_VALUE, null)).isTrue();
    }

    @Test
    void nuloLoDejaALaOtraRestriccion() {
        assertThat(validador.isValid(null, null)).isTrue();
    }
}
