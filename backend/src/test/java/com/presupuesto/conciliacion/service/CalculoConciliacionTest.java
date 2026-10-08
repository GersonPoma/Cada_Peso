package com.presupuesto.conciliacion.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.cuenta.entity.TipoCuenta;
import org.junit.jupiter.api.Test;

class CalculoConciliacionTest {

    @Test
    void laDiferenciaEsExtractoMenosConciliadoAlCorte() {
        assertThat(CalculoConciliacion.diferencia(300_000L, 300_000L)).isZero();
        assertThat(CalculoConciliacion.diferencia(310_000L, 300_000L)).isEqualTo(10_000L);
        assertThat(CalculoConciliacion.diferencia(290_000L, 300_000L)).isEqualTo(-10_000L);
    }

    @Test
    void conUnExtractoNegativoLaDiferenciaSigueElMismoSigno() {
        assertThat(CalculoConciliacion.diferencia(-50_000L, -45_000L)).isEqualTo(-5_000L);
        assertThat(CalculoConciliacion.diferencia(-40_000L, -45_000L)).isEqualTo(5_000L);
    }

    @Test
    void elAjustePositivoDeUnaCuentaNormalNoExigeCategoria() {
        assertThat(CalculoConciliacion.exigeCategoria(true, TipoCuenta.CORRIENTE, 10_000L))
                .isFalse();
    }

    @Test
    void elAjusteNegativoEnElPresupuestoExigeCategoria() {
        assertThat(CalculoConciliacion.exigeCategoria(true, TipoCuenta.CORRIENTE, -10_000L))
                .isTrue();
        assertThat(CalculoConciliacion.exigeCategoria(true, TipoCuenta.TARJETA_CREDITO, -1L))
                .isTrue();
    }

    @Test
    void elAjustePositivoDeUnaTarjetaExigeCategoria() {
        assertThat(CalculoConciliacion.exigeCategoria(true, TipoCuenta.TARJETA_CREDITO, 10_000L))
                .isTrue();
    }

    @Test
    void fueraDelPresupuestoNuncaExigeCategoria() {
        assertThat(CalculoConciliacion.exigeCategoria(false, TipoCuenta.CORRIENTE, -10_000L))
                .isFalse();
        assertThat(CalculoConciliacion.exigeCategoria(false, TipoCuenta.TARJETA_CREDITO, 10_000L))
                .isFalse();
    }
}
