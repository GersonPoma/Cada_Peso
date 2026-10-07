package com.presupuesto.meta.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class MetaPospuestaTest {

    private static MetaPospuesta en(LocalDate mes) {
        return MetaPospuesta.builder().meta(Meta.builder().build()).mes(mes).build();
    }

    @Test
    void elMesDebeSerElPrimerDia() {
        en(LocalDate.of(2026, 10, 1)).validarMes();

        MetaPospuesta diaQuince = en(LocalDate.of(2026, 10, 15));
        assertThrows(IllegalStateException.class, diaQuince::validarMes);
        assertThrows(IllegalStateException.class,
                () -> MetaPospuesta.builder().build().validarMes());
    }

    @Test
    void noExponeSetters() {
        assertThat(Arrays.stream(MetaPospuesta.class.getDeclaredMethods())
                        .map(Method::getName)
                        .filter(nombre -> nombre.startsWith("set")))
                .isEmpty();
    }
}
