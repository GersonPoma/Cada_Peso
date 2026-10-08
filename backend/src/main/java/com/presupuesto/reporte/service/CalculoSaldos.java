package com.presupuesto.reporte.service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Saldo al cierre de cada mes de una cuenta, como función pura (milésimas). El saldo de un mes es
 * {@code saldoInicial} más todos los movimientos hasta el último día de ese mes, también los
 * anteriores al rango pedido: el saldo de apertura del rango es el saldo inicial más todo lo
 * anterior a {@code desde}. La cuenta no tiene fecha de apertura, así que el saldo inicial cuenta
 * desde el primer mes.
 */
final class CalculoSaldos {

    /** Entradas (positivas) y salidas (negativas, con signo) de un mes. */
    record Movimiento(long entradas, long salidas) {}

    /** Entradas, salidas y saldo al cierre de un mes. */
    record SaldoMes(YearMonth mes, long entradas, long salidas, long saldo) {}

    private CalculoSaldos() {}

    /**
     * @param movimientos movimientos por mes, de todos los meses hasta {@code hasta}; los meses
     *     posteriores a {@code hasta} se ignoran
     * @return un elemento por cada mes del rango; un mes sin movimientos repite el saldo
     */
    static List<SaldoMes> alCierre(
            long saldoInicial, Map<YearMonth, Movimiento> movimientos, RangoMeses rango) {
        long saldo = saldoInicial;
        for (Map.Entry<YearMonth, Movimiento> fila : movimientos.entrySet()) {
            if (fila.getKey().isBefore(rango.desde())) {
                saldo += fila.getValue().entradas() + fila.getValue().salidas();
            }
        }
        List<SaldoMes> saldos = new ArrayList<>();
        for (YearMonth mes : rango.meses()) {
            Movimiento movimiento = movimientos.getOrDefault(mes, new Movimiento(0L, 0L));
            saldo += movimiento.entradas() + movimiento.salidas();
            saldos.add(new SaldoMes(mes, movimiento.entradas(), movimiento.salidas(), saldo));
        }
        return saldos;
    }
}
