package com.presupuesto.transaccion.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.cuenta.entity.Cuenta;
import org.junit.jupiter.api.Test;

class SaldoCuentaResponseTest {

    @Test
    void sumaElSaldoInicialALasSumasDeLasTransacciones() {
        Cuenta cuenta = Cuenta.builder().id(4L).saldoInicial(100_000L).build();

        SaldoCuentaResponse respuesta = SaldoCuentaResponse.desde(cuenta, -25_000L, -5_000L);

        assertThat(respuesta).isEqualTo(new SaldoCuentaResponse(4L, 75_000L, 95_000L));
    }

    @Test
    void sinTransaccionesElSaldoEsElInicial() {
        Cuenta cuenta = Cuenta.builder().id(4L).saldoInicial(-300L).build();

        assertThat(SaldoCuentaResponse.desde(cuenta, 0L, 0L))
                .isEqualTo(new SaldoCuentaResponse(4L, -300L, -300L));
    }
}
