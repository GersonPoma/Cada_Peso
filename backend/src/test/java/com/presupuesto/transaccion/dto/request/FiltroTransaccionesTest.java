package com.presupuesto.transaccion.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FiltroTransaccionesTest {

    @Test
    void qSeRecortaYVacioPasaANulo() {
        assertThat(filtro("  norte ").q()).isEqualTo("norte");
        assertThat(filtro("   ").q()).isNull();
        assertThat(filtro(null).q()).isNull();
    }

    private static FiltroTransacciones filtro(String q) {
        return new FiltroTransacciones(null, null, null, null, null, false, q);
    }
}
