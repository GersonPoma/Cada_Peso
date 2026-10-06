package com.presupuesto.cuenta.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.entity.TipoCuenta;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CuentaResponseTest {

    @Test
    void desdeCopiaCadaCampoDeLaCuenta() {
        Instant creado = Instant.parse("2026-10-02T12:00:00Z");
        Instant actualizado = Instant.parse("2026-10-03T12:00:00Z");
        Cuenta cuenta = Cuenta.builder()
                .id(7L)
                .nombre("Tarjeta")
                .nombreNormalizado("tarjeta")
                .tipo(TipoCuenta.TARJETA_CREDITO)
                .enPresupuesto(false)
                .saldoInicial(-250_000L)
                .cerrada(true)
                .fechaCreacion(creado)
                .fechaActualizacion(actualizado)
                .build();

        CuentaResponse response = CuentaResponse.desde(cuenta);

        assertThat(response).isEqualTo(new CuentaResponse(
                7L, "Tarjeta", TipoCuenta.TARJETA_CREDITO, false, -250_000L, true, creado,
                actualizado));
    }
}
