package com.presupuesto.comun.validacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaximoBytesUtf8ValidatorTest {

    private MaximoBytesUtf8Validator validador;

    @BeforeEach
    void crearValidador() {
        validador = new MaximoBytesUtf8Validator();
        validador.initialize(anotacionConMaximo(72));
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

    private static MaximoBytesUtf8 anotacionConMaximo(int maximo) {
        return new MaximoBytesUtf8() {
            @Override
            public int value() {
                return maximo;
            }

            @Override
            public String message() {
                return "";
            }

            @Override
            public Class<?>[] groups() {
                return new Class<?>[0];
            }

            @Override
            @SuppressWarnings("unchecked")
            public Class<? extends jakarta.validation.Payload>[] payload() {
                return new Class[0];
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return MaximoBytesUtf8.class;
            }
        };
    }
}
