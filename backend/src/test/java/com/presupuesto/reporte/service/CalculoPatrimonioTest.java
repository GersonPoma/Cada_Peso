package com.presupuesto.reporte.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.cuenta.entity.TipoCuenta;
import com.presupuesto.reporte.service.CalculoPatrimonio.CuentaConSaldos;
import com.presupuesto.reporte.service.CalculoPatrimonio.PatrimonioMes;
import com.presupuesto.reporte.service.CalculoSaldos.SaldoMes;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalculoPatrimonioTest {

    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);
    private static final RangoMeses SOLO_OCTUBRE = new RangoMeses(OCTUBRE, OCTUBRE);

    private static CuentaConSaldos cuenta(TipoCuenta tipo, long... saldos) {
        YearMonth mes = YearMonth.of(2026, 10);
        SaldoMes[] porMes = new SaldoMes[saldos.length];
        for (int i = 0; i < saldos.length; i++) {
            porMes[i] = new SaldoMes(mes.plusMonths(i), 0L, 0L, saldos[i]);
        }
        return new CuentaConSaldos(tipo, Arrays.asList(porMes));
    }

    @Test
    void elEjemploDeLaSpecDa1800000_3050000_y_menos1250000() {
        List<PatrimonioMes> resultado = CalculoPatrimonio.calcular(List.of(
                cuenta(TipoCuenta.CORRIENTE, 1_200_000L),
                cuenta(TipoCuenta.AHORRO, 600_000L),
                cuenta(TipoCuenta.TARJETA_CREDITO, -150_000L),
                cuenta(TipoCuenta.PRESTAMO, -2_900_000L)), SOLO_OCTUBRE);

        assertThat(resultado).containsExactly(
                new PatrimonioMes(OCTUBRE, 1_800_000L, 3_050_000L, -1_250_000L));
    }

    @Test
    void sumanComoActivoLasCuentasCorrienteAhorroEfectivoEInversion() {
        List<PatrimonioMes> resultado = CalculoPatrimonio.calcular(List.of(
                cuenta(TipoCuenta.CORRIENTE, 1L),
                cuenta(TipoCuenta.AHORRO, 20L),
                cuenta(TipoCuenta.EFECTIVO, 300L),
                cuenta(TipoCuenta.INVERSION, 4_000L)), SOLO_OCTUBRE);

        assertThat(resultado.get(0).activos()).isEqualTo(4_321L);
        assertThat(resultado.get(0).pasivos()).isZero();
        assertThat(resultado.get(0).patrimonio()).isEqualTo(4_321L);
    }

    @Test
    void unSobregiroEnUnaCuentaDeActivoRestaDeLosActivos() {
        List<PatrimonioMes> resultado = CalculoPatrimonio.calcular(List.of(
                cuenta(TipoCuenta.CORRIENTE, -5_000L),
                cuenta(TipoCuenta.AHORRO, 8_000L)), SOLO_OCTUBRE);

        assertThat(resultado.get(0).activos()).isEqualTo(3_000L);
        assertThat(resultado.get(0).patrimonio()).isEqualTo(3_000L);
    }

    @Test
    void unaTarjetaConSaldoAFavorDaUnPasivoNegativoYElPatrimonioSigueSiendoLaSumaDeSaldos() {
        List<CuentaConSaldos> cuentas = List.of(
                cuenta(TipoCuenta.CORRIENTE, 10_000L),
                cuenta(TipoCuenta.TARJETA_CREDITO, 2_500L));

        PatrimonioMes mes = CalculoPatrimonio.calcular(cuentas, SOLO_OCTUBRE).get(0);

        assertThat(mes.pasivos()).isEqualTo(-2_500L);
        assertThat(mes.patrimonio()).isEqualTo(12_500L);
    }

    @Test
    void elPatrimonioEsSiempreLaSumaDeTodosLosSaldosEnCadaMes() {
        List<CuentaConSaldos> cuentas = List.of(
                cuenta(TipoCuenta.CORRIENTE, 100L, 150L),
                cuenta(TipoCuenta.INVERSION, 50L, 60L),
                cuenta(TipoCuenta.TARJETA_CREDITO, -30L, -90L),
                cuenta(TipoCuenta.PRESTAMO, -1_000L, -900L));

        List<PatrimonioMes> resultado = CalculoPatrimonio.calcular(
                cuentas, new RangoMeses(OCTUBRE, OCTUBRE.plusMonths(1)));

        assertThat(resultado).extracting(PatrimonioMes::patrimonio)
                .containsExactly(100L + 50L - 30L - 1_000L, 150L + 60L - 90L - 900L);
        assertThat(resultado).extracting(PatrimonioMes::mes)
                .containsExactly(OCTUBRE, OCTUBRE.plusMonths(1));
    }

    @Test
    void sinCuentasCadaMesVale0() {
        List<PatrimonioMes> resultado = CalculoPatrimonio.calcular(
                List.of(), new RangoMeses(OCTUBRE, OCTUBRE.plusMonths(2)));

        assertThat(resultado).hasSize(3).allSatisfy(mes -> {
            assertThat(mes.activos()).isZero();
            assertThat(mes.pasivos()).isZero();
            assertThat(mes.patrimonio()).isZero();
        });
    }

    @Test
    void soloLaTarjetaYElPrestamoSonPasivos() {
        for (TipoCuenta tipo : TipoCuenta.values()) {
            boolean esperado = tipo == TipoCuenta.TARJETA_CREDITO || tipo == TipoCuenta.PRESTAMO;
            assertThat(CalculoPatrimonio.esPasivo(tipo)).as(tipo.name()).isEqualTo(esperado);
        }
    }
}
