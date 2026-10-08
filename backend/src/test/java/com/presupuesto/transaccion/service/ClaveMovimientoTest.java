package com.presupuesto.transaccion.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ClaveMovimientoTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 3, 5);

    @Test
    void ignoraLasMayusculasDelBeneficiario() {
        assertThat(ClaveMovimiento.de(FECHA, -4500, "Cafe LUNA"))
                .isEqualTo(ClaveMovimiento.de(FECHA, -4500, "cafe luna"));
    }

    @Test
    void sinBeneficiarioEquivaleAVacio() {
        assertThat(ClaveMovimiento.de(FECHA, 100, null))
                .isEqualTo(ClaveMovimiento.de(FECHA, 100, ""));
        assertThat(ClaveMovimiento.de(FECHA, 100, null).beneficiarioNormalizado()).isEmpty();
    }

    @Test
    void normalizaConLocaleRootSinDependerDeLaJvm() {
        assertThat(ClaveMovimiento.de(FECHA, 1, "ÁRBOL ÑANDÚ").beneficiarioNormalizado())
                .isEqualTo("árbol ñandú");
        assertThat(ClaveMovimiento.de(FECHA, 1, "I").beneficiarioNormalizado()).isEqualTo("i");
    }

    @Test
    void fechaOMontoDistintosDanClavesDistintas() {
        assertThat(ClaveMovimiento.de(FECHA, 100, "a"))
                .isNotEqualTo(ClaveMovimiento.de(FECHA.plusDays(1), 100, "a"))
                .isNotEqualTo(ClaveMovimiento.de(FECHA, 101, "a"));
    }
}
