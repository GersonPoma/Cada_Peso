package com.presupuesto.comun.validacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaximoBytesUtf8ValidatorTest {

    private MaximoBytesUtf8Validator validador;

    @BeforeEach
    void crearValidador() throws NoSuchFieldException {
        validador = new MaximoBytesUtf8Validator();
        validador.initialize(anotacionConMaximo72());
    }

    @Test
    void aceptaSetentaYDosCaracteresAscii() {
        assertThat(validador.isValid("a".repeat(72), null)).isTrue();
    }

    @Test
    void rechazaSetentaYDosCaracteresConUnaEnie() {
        String texto = "ñ" + "a".repeat(71);

        assertThat(texto).hasSize(72);
        assertThat(validador.isValid(texto, null)).isFalse();
    }

    @Test
    void aceptaTreintaYSeisEniesQueOcupanSetentaYDosBytes() {
        assertThat(validador.isValid("ñ".repeat(36), null)).isTrue();
    }

    @Test
    void aceptaNull() {
        assertThat(validador.isValid(null, null)).isTrue();
    }

    /** Campo de apoyo: la anotación se lee de aquí en vez de implementarla a mano. */
    @MaximoBytesUtf8(72)
    private static String campoConMaximo72;

    private static MaximoBytesUtf8 anotacionConMaximo72() throws NoSuchFieldException {
        return MaximoBytesUtf8ValidatorTest.class
                .getDeclaredField("campoConMaximo72")
                .getAnnotation(MaximoBytesUtf8.class);
    }
}
