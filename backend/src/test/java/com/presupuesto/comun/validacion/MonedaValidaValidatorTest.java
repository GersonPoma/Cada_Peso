package com.presupuesto.comun.validacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MonedaValidaValidatorTest {

    private final MonedaValidaValidator validador = new MonedaValidaValidator();

    @ParameterizedTest
    @ValueSource(strings = {"BOB", "USD"})
    void aceptaCodigosDeMonedaExistentes(String codigo) {
        assertThat(validador.isValid(codigo, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ZZZ", "bob", " USD", ""})
    void rechazaCodigosInexistentesOMalEscritos(String codigo) {
        assertThat(validador.isValid(codigo, null)).isFalse();
    }

    @Test
    void aceptaNull() {
        assertThat(validador.isValid(null, null)).isTrue();
    }
}
