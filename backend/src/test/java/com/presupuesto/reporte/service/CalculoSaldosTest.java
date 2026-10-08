package com.presupuesto.reporte.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.reporte.service.CalculoSaldos.Movimiento;
import com.presupuesto.reporte.service.CalculoSaldos.SaldoMes;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CalculoSaldosTest {

    private static RangoMeses rango(YearMonth desde, YearMonth hasta) {
        return new RangoMeses(desde, hasta);
    }

    private static YearMonth mes(int anio, int mes) {
        return YearMonth.of(anio, mes);
    }

    @Test
    void elEjemploDeSeptiembreANoviembre() {
        Map<YearMonth, Movimiento> movimientos = new HashMap<>();
        movimientos.put(mes(2026, 9), new Movimiento(50_000L, -20_000L));
        movimientos.put(mes(2026, 11), new Movimiento(0L, -30_000L));

        List<SaldoMes> saldos =
                CalculoSaldos.alCierre(100_000L, movimientos, rango(mes(2026, 9), mes(2026, 11)));

        assertThat(saldos).containsExactly(
                new SaldoMes(mes(2026, 9), 50_000L, -20_000L, 130_000L),
                new SaldoMes(mes(2026, 10), 0L, 0L, 130_000L),
                new SaldoMes(mes(2026, 11), 0L, -30_000L, 100_000L));
    }

    @Test
    void unRangoPosteriorAMovimientosPreviosParteDelSaldoDeApertura() {
        Map<YearMonth, Movimiento> movimientos = new HashMap<>();
        movimientos.put(mes(2025, 11), new Movimiento(40_000L, 0L));
        movimientos.put(mes(2025, 12), new Movimiento(0L, -10_000L));
        movimientos.put(mes(2026, 1), new Movimiento(5_000L, -2_000L));

        List<SaldoMes> saldos =
                CalculoSaldos.alCierre(100_000L, movimientos, rango(mes(2026, 1), mes(2026, 2)));

        // apertura: 100000 + 40000 - 10000 = 130000; sin ella enero daría 103000
        assertThat(saldos).containsExactly(
                new SaldoMes(mes(2026, 1), 5_000L, -2_000L, 133_000L),
                new SaldoMes(mes(2026, 2), 0L, 0L, 133_000L));
        assertThat(saldos.get(0).saldo()).isNotEqualTo(103_000L);
    }

    @Test
    void sinMovimientosCadaMesRepiteElSaldoInicial() {
        List<SaldoMes> saldos =
                CalculoSaldos.alCierre(70_000L, Map.of(), rango(mes(2026, 1), mes(2026, 3)));

        assertThat(saldos).hasSize(3).allSatisfy(saldo -> {
            assertThat(saldo.entradas()).isZero();
            assertThat(saldo.salidas()).isZero();
            assertThat(saldo.saldo()).isEqualTo(70_000L);
        });
    }

    @Test
    void unSaldoInicialNegativoSeArrastraComoDeuda() {
        Map<YearMonth, Movimiento> movimientos = Map.of(mes(2026, 1), new Movimiento(50_000L, 0L));

        List<SaldoMes> saldos =
                CalculoSaldos.alCierre(-200_000L, movimientos, rango(mes(2026, 1), mes(2026, 2)));

        assertThat(saldos).extracting(SaldoMes::saldo).containsExactly(-150_000L, -150_000L);
    }

    @Test
    void losMesesPosterioresAlRangoSeIgnoran() {
        Map<YearMonth, Movimiento> movimientos = Map.of(
                mes(2026, 1), new Movimiento(10_000L, 0L),
                mes(2026, 6), new Movimiento(999_000L, 0L));

        List<SaldoMes> saldos =
                CalculoSaldos.alCierre(0L, movimientos, rango(mes(2026, 1), mes(2026, 2)));

        assertThat(saldos).extracting(SaldoMes::saldo).containsExactly(10_000L, 10_000L);
    }
}
