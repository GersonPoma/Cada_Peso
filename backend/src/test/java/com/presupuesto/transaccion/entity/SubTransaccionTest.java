package com.presupuesto.transaccion.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SubTransaccionTest {

    @Test
    void elBuilderAsignaMontoYMemo() {
        SubTransaccion sub = SubTransaccion.builder().monto(-500L).memo("pan").build();

        assertThat(sub.getMonto()).isEqualTo(-500L);
        assertThat(sub.getMemo()).isEqualTo("pan");
        assertThat(sub.getCategoria()).isNull();
    }

    @Test
    void noExponeSettersDeNegocio() {
        assertThat(Arrays.stream(SubTransaccion.class.getDeclaredMethods())
                        .map(Method::getName)
                        .filter(n -> n.startsWith("set")))
                .isEmpty();
    }
}
