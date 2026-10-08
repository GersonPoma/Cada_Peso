package com.presupuesto.conciliacion.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CrearConciliacionRequestTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 9, 10);

    @Test
    void crearAjusteAusenteOFalsoNoPideAjuste() {
        assertThat(new CrearConciliacionRequest(1L, FECHA, null, null).pideAjuste()).isFalse();
        assertThat(new CrearConciliacionRequest(1L, FECHA, false, null).pideAjuste()).isFalse();
    }

    @Test
    void crearAjusteVerdaderoPideAjuste() {
        assertThat(new CrearConciliacionRequest(1L, FECHA, true, null).pideAjuste()).isTrue();
    }
}
