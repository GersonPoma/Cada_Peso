package com.presupuesto.conciliacion.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.transaccion.entity.Transaccion;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class EstadoConciliacionResponseTest {

    @Test
    void mapeaLasPendientesYConservaLosMontos() {
        Transaccion pendiente = Transaccion.builder()
                .id(9L)
                .cuenta(Cuenta.builder().id(3L).build())
                .fecha(LocalDate.of(2026, 9, 3))
                .monto(-120_000L)
                .build();

        EstadoConciliacionResponse estado = EstadoConciliacionResponse.desde(
                3L, LocalDate.of(2026, 9, 10), 300_000L, 250_000L, 300_000L, 0L, 120L,
                List.of(pendiente));

        assertThat(estado.saldoConciliado()).isEqualTo(250_000L);
        assertThat(estado.saldoConciliadoAlCorte()).isEqualTo(300_000L);
        assertThat(estado.diferencia()).isZero();
        assertThat(estado.totalNoConciliadas()).isEqualTo(120L);
        assertThat(estado.noConciliadas()).hasSize(1);
        assertThat(estado.noConciliadas().get(0).id()).isEqualTo(9L);
    }
}
