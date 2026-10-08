package com.presupuesto.conciliacion.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.conciliacion.entity.Conciliacion;
import com.presupuesto.cuenta.entity.Cuenta;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ConciliacionResponseTest {

    @Test
    void copiaTodosLosCamposDelRegistro() {
        Instant creada = Instant.parse("2026-10-02T12:00:00Z");
        Conciliacion registro = Conciliacion.builder()
                .id(7L)
                .cuenta(Cuenta.builder().id(3L).build())
                .fecha(LocalDate.of(2026, 9, 10))
                .saldoExtracto(310_000L)
                .ajuste(10_000L)
                .transaccionAjusteId(55L)
                .cantidadReconciliadas(3)
                .fechaCreacion(creada)
                .build();

        assertThat(ConciliacionResponse.desde(registro)).isEqualTo(new ConciliacionResponse(
                7L, 3L, LocalDate.of(2026, 9, 10), 310_000L, 10_000L, 55L, 3, creada));
    }

    @Test
    void sinAjusteElIdDelAjusteEsNulo() {
        Conciliacion registro = Conciliacion.builder()
                .cuenta(Cuenta.builder().id(3L).build())
                .fecha(LocalDate.of(2026, 9, 10))
                .build();

        assertThat(ConciliacionResponse.desde(registro).transaccionAjusteId()).isNull();
        assertThat(ConciliacionResponse.desde(registro).ajuste()).isZero();
    }
}
